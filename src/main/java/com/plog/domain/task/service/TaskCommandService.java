package com.plog.domain.task.service;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.task.dto.request.TaskAttachmentAddRequest;
import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.dto.request.TaskUpdateRequest;
import com.plog.domain.task.dto.response.*;
import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.domain.task.event.TaskAttachmentAddedEvent;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.AttachmentUsage;
import com.plog.infrastructure.s3.UploadedFile;
import com.plog.infrastructure.s3.UploadedFileService;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 업무카드 생성/수정/삭제 + 첨부파일 등록/삭제 — 쓰기 전용
@Service
public class TaskCommandService {

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ReportActivityLogRepository reportActivityLogRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final AttachmentPolicy attachmentPolicy;
    private final UploadedFileService uploadedFileService;
    private final TaskAttachmentUrlResolver urlResolver;
    private final ApplicationEventPublisher eventPublisher;

    public TaskCommandService(TaskRepository taskRepository,
                              TaskAttachmentRepository taskAttachmentRepository,
                              ReportActivityLogRepository reportActivityLogRepository,
                              ProjectMemberRepository projectMemberRepository,
                              ProjectAccessService projectAccessService,
                              AttachmentPolicy attachmentPolicy,
                              UploadedFileService uploadedFileService,
                              TaskAttachmentUrlResolver urlResolver,
                              ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.reportActivityLogRepository = reportActivityLogRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectAccessService = projectAccessService;
        this.attachmentPolicy = attachmentPolicy;
        this.uploadedFileService = uploadedFileService;
        this.urlResolver = urlResolver;
        this.eventPublisher = eventPublisher;
    }

    // 업무 카드 생성
    @Transactional
    public TaskCreateResponse createTask(Long projectId, Long userId, TaskCreateRequest request) {
        projectAccessService.requireActiveMember(projectId, userId);

        ProjectMember assignee = projectMemberRepository.findById(request.projectMemberId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.ASSIGNEE_NOT_FOUND));

        if (!assignee.getProject().getId().equals(projectId)) {
            throw new ApiException(TaskErrorCode.ASSIGNEE_PROJECT_MISMATCH);
        }
        if (assignee.getStatus() != MemberStatus.ACTIVE) {
            throw new ApiException(TaskErrorCode.ASSIGNEE_NOT_ACTIVE);
        }

        ProjectType projectType = assignee.getProject().getProjectType();
        if (!request.category().isAllowedFor(projectType)) {
            throw new ApiException(TaskErrorCode.INVALID_CATEGORY_FOR_PROJECT_TYPE);
        }

        Task task = Task.create(assignee, request.title(), request.category(),
                request.cardStatus(), request.endDate());
        taskRepository.save(task);

        List<TaskAttachment> attachments = createAttachments(task, userId, request.attachments());

        List<TaskCreateResponse.AttachmentResponse> attachmentResponses = attachments.stream()
                .map(attachment -> TaskCreateResponse.AttachmentResponse.of(
                        attachment, urlResolver.resolveDownloadUrlApi(projectId, attachment)))
                .toList();
        return TaskCreateResponse.from(task, attachmentResponses);
    }

    // 업무카드 수정
    @Transactional
    public TaskUpdateResponse updateTask(Long projectId, Long taskId, Long userId, TaskUpdateRequest request) {
        projectAccessService.requireActiveMember(projectId, userId);

        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        if (request.title() != null) {
            task.changeTitle(request.title());
        }

        if (request.projectMemberId() != null) {
            ProjectMember newAssignee = projectMemberRepository.findById(request.projectMemberId())
                    .orElseThrow(() -> new ApiException(TaskErrorCode.ASSIGNEE_NOT_FOUND));
            if (!newAssignee.getProject().getId().equals(projectId)) {
                throw new ApiException(TaskErrorCode.ASSIGNEE_PROJECT_MISMATCH);
            }
            if (newAssignee.getStatus() != MemberStatus.ACTIVE) {
                throw new ApiException(TaskErrorCode.ASSIGNEE_NOT_ACTIVE);
            }
            task.changeAssignee(newAssignee);
        }

        if (request.category() != null) {
            ProjectType projectType = task.getProjectMember().getProject().getProjectType();
            if (!request.category().isAllowedFor(projectType)) {
                throw new ApiException(TaskErrorCode.INVALID_CATEGORY_FOR_PROJECT_TYPE);
            }
            task.changeCategory(request.category());
        }

        if (request.endDate() != null) {
            task.changeEndDate(request.endDate());
        }

        List<TaskUpdateResponse.AttachmentResponse> attachments = taskAttachmentRepository
                .findAllByTaskId(taskId).stream()
                .map(attachment -> TaskUpdateResponse.AttachmentResponse.of(
                        attachment, urlResolver.resolveDownloadUrlApi(projectId, attachment)))
                .toList();

        return TaskUpdateResponse.from(task, attachments);
    }

    // 업무카드 삭제
    @Transactional
    public TaskDeleteResponse deleteTask(Long projectId, Long taskId, Long userId) {
        projectAccessService.requireActiveMember(projectId, userId);

        reportActivityLogRepository.acquireSourceLock(SourceDomain.TASK.name() + ":task:" + taskId);
        Task task = taskRepository.findByIdForUpdate(taskId)
                .filter(lockedTask -> lockedTask.getProjectMember().getProject().getId().equals(projectId))
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        List<TaskAttachment> attachments = taskAttachmentRepository.findAllByTaskId(taskId);
        List<Long> fileIds = taskAttachmentRepository.findFileIdsByTaskId(taskId);

        // 리포트 생성 여부와 무관하게 상태 변경/첨부 활동 로그가 업무를 FK로 참조한다.
        // 업무 삭제 전에 해당 원천 활동도 함께 제거해야 tasks 삭제가 FK 제약에 막히지 않는다.
        reportActivityLogRepository.deleteAllByLinkedTaskId(taskId);

        taskAttachmentRepository.deleteAll(attachments);
        taskAttachmentRepository.flush();

        taskRepository.delete(task);
        taskRepository.flush();

        uploadedFileService.release(fileIds);

        return new TaskDeleteResponse(true);
    }

    // 첨부파일 등록 — 기존 카드에 산출물 단건 추가
    @Transactional
    public TaskAttachmentAddResponse addAttachment(
            Long projectId, Long taskId, Long userId, TaskAttachmentAddRequest request) {
        projectAccessService.requireActiveMember(projectId, userId);

        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        taskRepository.findByIdForUpdate(taskId);

        long existingCount = taskAttachmentRepository.countByTaskId(taskId);
        attachmentPolicy.validateCount((int) existingCount + 1, TaskErrorCode.TASK_ATTACHMENT_LIMIT_EXCEEDED);

        TaskAttachment attachment;
        if (request.attachmentType() == AttachmentType.FILE) {
            UploadedFile file = attachmentPolicy.confirmFileAttachment(AttachmentUsage.TASK,
                    userId, request.fileName(), request.fileSize(), request.fileKey(),
                    TaskErrorCode.INVALID_ATTACHMENT);
            attachment = TaskAttachment.create(task, AttachmentType.FILE, request.fileSize(),
                    file, null, null);
        } else {
            attachmentPolicy.validateLink(request.linkUrl(), TaskErrorCode.INVALID_LINK_URL);
            attachment = TaskAttachment.create(task, AttachmentType.LINK, request.fileSize(),
                    null, request.linkUrl(), request.fileName());
        }
        taskAttachmentRepository.save(attachment);
        publishAttachmentAdded(task, attachment);

        return TaskAttachmentAddResponse.of(attachment, urlResolver.resolveDownloadUrlApi(projectId, attachment));
    }

    // 첨부파일 삭제
    @Transactional
    public TaskDeleteResponse deleteAttachment(
            Long projectId, Long taskId, Long taskAttachmentId, Long userId) {
        projectAccessService.requireActiveMember(projectId, userId);

        reportActivityLogRepository.acquireSourceLock(SourceDomain.TASK.name() + ":task:" + taskId);
        taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        TaskAttachment attachment = taskAttachmentRepository.findByIdAndTaskId(taskAttachmentId, taskId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_ATTACHMENT_NOT_FOUND));

        UploadedFile file = attachment.getUploadedFile();

        // 삭제된 산출물이 이후 리포트에서 DELIVERABLE_SUBMIT 근거로 집계되지 않도록
        // 첨부 원천 로그도 같은 트랜잭션에서 제거한다.
        reportActivityLogRepository.acquireSourceLock(
                SourceDomain.TASK.name() + ":task-attachment:" + taskAttachmentId);
        reportActivityLogRepository.deleteBySourceDomainAndSourceRefId(
                SourceDomain.TASK, "task-attachment:" + taskAttachmentId);
        taskAttachmentRepository.delete(attachment);
        taskAttachmentRepository.flush();

        if (file != null) {
            uploadedFileService.release(List.of(file.getId()));
        }

        return new TaskDeleteResponse(true);
    }

    // 업무카드 생성할 때 첨부파일 함께 등록
    private List<TaskAttachment> createAttachments(
            Task task, Long userId, List<TaskCreateRequest.TaskAttachmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        attachmentPolicy.validateCount(requests.size(), TaskErrorCode.TASK_ATTACHMENT_LIMIT_EXCEEDED);
        List<TaskAttachment> attachments = requests.stream().map(request -> {
            if (request.attachmentType() != AttachmentType.FILE) {
                attachmentPolicy.validateLink(request.linkUrl(), TaskErrorCode.INVALID_LINK_URL);
                return TaskAttachment.create(task, AttachmentType.LINK, request.fileSize(),
                        null, request.linkUrl(), request.fileName());
            }
            UploadedFile file = attachmentPolicy.confirmFileAttachment(AttachmentUsage.TASK,
                    userId, request.fileName(), request.fileSize(), request.fileKey(),
                    TaskErrorCode.INVALID_ATTACHMENT);
            return TaskAttachment.create(task, AttachmentType.FILE, request.fileSize(),
                    file, null, null);
        }).toList();
        taskAttachmentRepository.saveAll(attachments);
        attachments.forEach(attachment -> publishAttachmentAdded(task, attachment));
        return attachments;
    }

    // report 0단계 수집용 — 카드 생성 시 동봉 첨부/기존 카드 추가 첨부 모두 여기를 거친다.
    private void publishAttachmentAdded(Task task, TaskAttachment attachment) {
        LocalDateTime occurredAt = TimeUtil.nowUtc();
        eventPublisher.publishEvent(new TaskAttachmentAddedEvent(
                attachment.getId(), task.getId(), task.getProjectMember().getId(), occurredAt));
    }
}
