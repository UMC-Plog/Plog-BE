package com.plog.domain.task.service;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.request.TaskAttachmentAddRequest;
import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.dto.request.TaskUpdateRequest;
import com.plog.domain.task.dto.response.*;
import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.AttachmentUsage;
import com.plog.infrastructure.s3.FileDeletionEvent;
import com.plog.infrastructure.s3.FilePromotionEvent;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 업무카드 생성/수정/삭제 + 첨부파일 등록/삭제 — 쓰기 전용
@Service
public class TaskCommandService {

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final AttachmentPolicy attachmentPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskAttachmentUrlResolver urlResolver;

    public TaskCommandService(TaskRepository taskRepository,
                              TaskAttachmentRepository taskAttachmentRepository,
                              ProjectMemberRepository projectMemberRepository,
                              ProjectAccessService projectAccessService,
                              AttachmentPolicy attachmentPolicy,
                              ApplicationEventPublisher eventPublisher,
                              TaskAttachmentUrlResolver urlResolver) {
        this.taskRepository = taskRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectAccessService = projectAccessService;
        this.attachmentPolicy = attachmentPolicy;
        this.eventPublisher = eventPublisher;
        this.urlResolver = urlResolver;
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
        publishPromotions(attachments);

        List<TaskCreateResponse.AttachmentResponse> attachmentResponses = attachments.stream()
                .map(attachment -> TaskCreateResponse.AttachmentResponse.of(
                        attachment, urlResolver.resolve(attachment)))
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
                        attachment, urlResolver.resolve(attachment)))
                .toList();

        return TaskUpdateResponse.from(task, attachments);
    }

    // 업무카드 삭제
    @Transactional
    public TaskDeleteResponse deleteTask(Long projectId, Long taskId, Long userId) {
        projectAccessService.requireActiveMember(projectId, userId);

        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        List<TaskAttachment> attachments = taskAttachmentRepository.findAllByTaskId(taskId);
        List<String> fileKeys = attachments.stream()
                .filter(attachment -> attachment.getAttachmentType() == AttachmentType.FILE)
                .map(TaskAttachment::getFileUrl)
                .toList();

        taskAttachmentRepository.deleteAll(attachments);
        taskAttachmentRepository.flush();

        taskRepository.delete(task);
        taskRepository.flush();

        if (!fileKeys.isEmpty()) {
            eventPublisher.publishEvent(new FileDeletionEvent(fileKeys));
        }

        return new TaskDeleteResponse(true);
    }

    // 첨부파일 등록 — 기존 카드에 산출물 단건 추가
    @Transactional
    public TaskAttachmentAddResponse addAttachment(
            Long projectId, Long taskId, Long userId, TaskAttachmentAddRequest request) {
        projectAccessService.requireActiveMember(projectId, userId);

        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        if (request.attachmentType() == AttachmentType.EXTERNAL) {
            throw new ApiException(TaskErrorCode.INVALID_ATTACHMENT);
        }

        taskRepository.findByIdForUpdate(taskId);

        long existingCount = taskAttachmentRepository.countByTaskId(taskId);
        attachmentPolicy.validateCount((int) existingCount + 1, TaskErrorCode.TASK_ATTACHMENT_LIMIT_EXCEEDED);

        String storedValue;
        if (request.attachmentType() == AttachmentType.FILE) {
            attachmentPolicy.validateFileAttachment(AttachmentUsage.TASK, userId,
                    request.fileName(), request.fileSize(), request.fileKey(),
                    TaskErrorCode.INVALID_ATTACHMENT);
            storedValue = request.fileKey();
        } else {
            attachmentPolicy.validateLink(request.fileUrl(), TaskErrorCode.INVALID_LINK_URL);
            storedValue = request.fileUrl();
        }

        TaskAttachment attachment = TaskAttachment.create(
                task, request.attachmentType(), request.fileName(), request.fileSize(), storedValue);
        taskAttachmentRepository.save(attachment);
        if (request.attachmentType() == AttachmentType.FILE) {
            eventPublisher.publishEvent(new FilePromotionEvent(List.of(storedValue)));
        }

        return TaskAttachmentAddResponse.of(attachment, urlResolver.resolve(attachment));
    }

    // 첨부파일 삭제
    @Transactional
    public TaskDeleteResponse deleteAttachment(
            Long projectId, Long taskId, Long taskAttachmentId, Long userId) {
        projectAccessService.requireActiveMember(projectId, userId);

        taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        TaskAttachment attachment = taskAttachmentRepository.findByIdAndTaskId(taskAttachmentId, taskId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_ATTACHMENT_NOT_FOUND));

        boolean isFile = attachment.getAttachmentType() == AttachmentType.FILE;
        String fileKey = attachment.getFileUrl();

        taskAttachmentRepository.delete(attachment);
        taskAttachmentRepository.flush();

        if (isFile) {
            eventPublisher.publishEvent(new FileDeletionEvent(List.of(fileKey)));
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
        for (TaskCreateRequest.TaskAttachmentRequest request : requests) {
            if (request.attachmentType() == AttachmentType.EXTERNAL) {
                throw new ApiException(TaskErrorCode.INVALID_ATTACHMENT);
            }
            if (request.attachmentType() == AttachmentType.FILE) {
                attachmentPolicy.validateFileAttachment(AttachmentUsage.TASK, userId,
                        request.fileName(), request.fileSize(), request.fileKey(),
                        TaskErrorCode.INVALID_ATTACHMENT);
            } else {
                attachmentPolicy.validateLink(request.fileUrl(), TaskErrorCode.INVALID_LINK_URL);
            }
        }
        List<TaskAttachment> attachments = requests.stream()
                .map(r -> TaskAttachment.create(task, r.attachmentType(), r.fileName(), r.fileSize(),
                        r.attachmentType() == AttachmentType.FILE ? r.fileKey() : r.fileUrl()))
                .toList();
        taskAttachmentRepository.saveAll(attachments);
        return attachments;
    }

    private void publishPromotions(List<TaskAttachment> attachments) {
        List<String> fileKeys = attachments.stream()
                .filter(attachment -> attachment.getAttachmentType() == AttachmentType.FILE)
                .map(TaskAttachment::getFileUrl)
                .toList();
        if (!fileKeys.isEmpty()) {
            eventPublisher.publishEvent(new FilePromotionEvent(fileKeys));
        }
    }
}