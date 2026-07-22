package com.plog.domain.task.service;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.dto.request.TaskUpdateRequest;
import com.plog.domain.task.dto.response.TaskUpdateResponse;
import com.plog.domain.task.dto.response.TaskCreateResponse;
import com.plog.domain.task.dto.response.TaskDetailResponse;
import com.plog.domain.task.dto.response.TaskListResponse;
import com.plog.domain.task.dto.response.TaskSummaryResponse;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskAttachmentRepository.TaskAttachmentCount;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.global.api.exception.ApiException;
import com.plog.domain.task.entity.AttachmentType;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.AttachmentUsage;
import com.plog.infrastructure.s3.FilePromotionEvent;
import com.plog.infrastructure.s3.FileStorageService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final AttachmentPolicy attachmentPolicy;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(TaskRepository taskRepository,
                       TaskAttachmentRepository taskAttachmentRepository,
                       ProjectMemberRepository projectMemberRepository,
                       ProjectAccessService projectAccessService,
                       AttachmentPolicy attachmentPolicy,
                       FileStorageService fileStorageService,
                       ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectAccessService = projectAccessService;
        this.attachmentPolicy = attachmentPolicy;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
    }

    // 업무 카드 생성
    @Transactional
    public TaskCreateResponse createTask(Long projectId, Long userId, TaskCreateRequest request) {
        // 1) 로그인 사용자가 해당 프로젝트의 활성 멤버인지 검증
        projectAccessService.requireActiveMember(projectId, userId);

        // 2) 담당자로 지정된 ProjectMember 조회
        ProjectMember assignee = projectMemberRepository.findById(request.projectMemberId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.ASSIGNEE_NOT_FOUND));

        // 3) 담당자가 URL의 projectId와 같은 프로젝트 소속인지 검증
        if (!assignee.getProject().getId().equals(projectId)) {
            throw new ApiException(TaskErrorCode.ASSIGNEE_PROJECT_MISMATCH);
        }

        // 4) 담당자가 "참여 중"인 멤버인지 검증 — 나간(EXIT) 팀원은 담당자로 지정 불가
        if (assignee.getStatus() != MemberStatus.ACTIVE) {
            throw new ApiException(TaskErrorCode.ASSIGNEE_NOT_ACTIVE);
        }

        // 5) 담당 영역이 프로젝트 유형(개발/일반)에 허용된 값인지 검증
        ProjectType projectType = assignee.getProject().getProjectType();
        if (!request.category().isAllowedFor(projectType)) {
            throw new ApiException(TaskErrorCode.INVALID_CATEGORY_FOR_PROJECT_TYPE);
        }

        // 6) Task 생성 — projectMember로만 연결, project_id는 저장하지 않음
        Task task = Task.create(assignee, request.title(), request.category(),
                request.cardStatus(), request.endDate());
        taskRepository.save(task);

        // 7) 첨부 자료(선택) 함께 생성
        List<TaskAttachment> attachments = createAttachments(task, userId, request.attachments());

        // 8) 커밋 후 업로드 파일을 영구 파일로 승격 — 빠뜨리면 수명 주기 규칙이 하루 뒤 삭제한다.
        publishPromotions(attachments);

        List<TaskCreateResponse.AttachmentResponse> attachmentResponses = attachments.stream()
                .map(attachment -> TaskCreateResponse.AttachmentResponse.of(
                        attachment, resolveUrl(attachment)))
                .toList();
        return TaskCreateResponse.from(task, attachmentResponses);
    }

    // 업무카드 목록 조회
    @Transactional(readOnly = true)
    public TaskListResponse getTaskList(Long projectId, Long userId) {
        // 1) 로그인 사용자가 해당 프로젝트의 활성 멤버인지 검증 (프로젝트 존재 여부도 함께 검증됨)
        projectAccessService.requireActiveMember(projectId, userId);

        // 2) Task -> ProjectMember 를 EntityGraph로 함께 조회해 N+1 방지
        List<Task> tasks = taskRepository.findAllByProjectMember_Project_IdOrderByCreatedAtAsc(projectId);
        if (tasks.isEmpty()) {
            return TaskListResponse.of(List.of());
        }

        // 3) 첨부파일은 이제 "개수"만 필요하므로, 전체 엔티티를 가져오지 않고
        //    taskId별 count(*) 집계 쿼리 하나로 끝낸다 (다운로드 URL 발급도 필요 없어짐).
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        Map<Long, Long> attachmentCountByTaskId = taskAttachmentRepository.countByTaskIds(taskIds).stream()
                .collect(Collectors.toMap(TaskAttachmentCount::getTaskId, TaskAttachmentCount::getCount));

        // 4) Entity를 그대로 넘기지 않고 DTO로 변환
        List<TaskSummaryResponse> content = tasks.stream()
                .map(task -> TaskSummaryResponse.from(
                        task,
                        attachmentCountByTaskId.getOrDefault(task.getId(), 0L).intValue()))
                .toList();

        return TaskListResponse.of(content);
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskDetail(Long projectId, Long taskId, Long userId) {
        projectAccessService.requireActiveMember(projectId, userId);

        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        List<TaskDetailResponse.AttachmentResponse> attachments = taskAttachmentRepository
                .findAllByTaskId(taskId).stream()
                .map(attachment -> TaskDetailResponse.AttachmentResponse.of(
                        attachment, resolveUrl(attachment)))
                .toList();

        return TaskDetailResponse.from(task, attachments);
    }

    // 업무카드 수정
    @Transactional
    public TaskUpdateResponse updateTask(Long projectId, Long taskId, Long userId, TaskUpdateRequest request) {
        // 1) 활성 멤버면 누구나 수정 가능 (담당자 여부와 무관)
        projectAccessService.requireActiveMember(projectId, userId);

        // 2) 소속 검증까지 포함된 단건 조회
        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        // 3) 값이 들어온 필드만 개별적으로 변경 (PATCH 부분 수정)
        if (request.title() != null) {
            task.changeTitle(request.title());
        }

        if (request.projectMemberId() != null) {
            // 담당자 변경 시 생성 때와 동일한 검증을 다시 거친다 —
            // 그렇지 않으면 생성에서 막던 "다른 프로젝트 소속/나간 멤버 지정"을 수정으로 우회할 수 있다.
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

        // 4) 첨부파일은 이번 수정 대상이 아니므로 있는 그대로 응답에만 포함
        List<TaskUpdateResponse.AttachmentResponse> attachments = taskAttachmentRepository
                .findAllByTaskId(taskId).stream()
                .map(attachment -> TaskUpdateResponse.AttachmentResponse.of(
                        attachment, resolveUrl(attachment)))
                .toList();

        return TaskUpdateResponse.from(task, attachments);
    }

    private List<TaskAttachment> createAttachments(
            Task task, Long userId, List<TaskCreateRequest.TaskAttachmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        attachmentPolicy.validateCount(requests.size(), TaskErrorCode.INVALID_ATTACHMENT);
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
        // FILE 은 S3 키를, LINK 는 원본 URL을 저장한다.
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

    private String resolveUrl(TaskAttachment attachment) {
        return attachment.getAttachmentType() == AttachmentType.FILE
                ? fileStorageService.createDownloadUrl(
                        AttachmentUsage.TASK, attachment.getFileUrl(), attachment.getFileName())
                : attachment.getFileUrl();
    }
}