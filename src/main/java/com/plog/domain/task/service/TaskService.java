package com.plog.domain.task.service;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.request.TaskAttachmentAddRequest;
import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.dto.request.TaskStatusUpdateRequest;
import com.plog.domain.task.dto.request.TaskUpdateRequest;
import com.plog.domain.task.dto.response.*;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskAttachmentRepository.TaskAttachmentCount;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.global.api.exception.ApiException;
import com.plog.domain.task.entity.AttachmentType;
import com.plog.infrastructure.s3.*;

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

    // 업무카드 상세 목록 조회
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

    // 업무카드 삭제
    @Transactional
    public TaskDeleteResponse deleteTask(Long projectId, Long taskId, Long userId) {
        // 1) 활성 멤버면 누구나 삭제 가능
        projectAccessService.requireActiveMember(projectId, userId);

        // 2) 소속 검증까지 포함된 단건 조회
        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        // 3) 첨부파일 먼저 조회 — FILE 타입 fileKey는 S3 정리용으로 미리 뽑아둔다
        List<TaskAttachment> attachments = taskAttachmentRepository.findAllByTaskId(taskId);
        List<String> fileKeys = attachments.stream()
                .filter(attachment -> attachment.getAttachmentType() == AttachmentType.FILE)
                .map(TaskAttachment::getFileUrl)
                .toList();

        // 4) 자식(TaskAttachment)을 부모(Task)보다 먼저 삭제한다.
        taskAttachmentRepository.deleteAll(attachments);
        taskAttachmentRepository.flush();

        // 5) 이제 자식이 없으니 Task 삭제
        taskRepository.delete(task);
        taskRepository.flush();

        // 6) 커밋 이후 S3 실물 파일 삭제를 비동기로 요청
        if (!fileKeys.isEmpty()) {
            eventPublisher.publishEvent(new FileDeletionEvent(fileKeys));
        }

        return new TaskDeleteResponse(true);
    }

    // 업무카드 상태 변경 전용 API
    @Transactional
    public TaskStatusUpdateResponse updateTaskStatus(
            Long projectId, Long taskId, Long userId, TaskStatusUpdateRequest request) {
        // 1) 활성 멤버면 누구나 상태 변경 가능 (수정/삭제와 동일 정책)
        projectAccessService.requireActiveMember(projectId, userId);

        // 2) 소속 검증까지 포함된 단건 조회
        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        // 3) 상태 전이 제한 없음 — 요청된 상태로 그대로 변경.
        //    completedAt은 Task.changeStatus() 내부에서 DONE 여부에 따라 자동으로 세팅/초기화된다.
        task.changeStatus(request.cardStatus());

        return TaskStatusUpdateResponse.from(task);
    }

    // 업무카드 생성할 때
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

    // 첨부파일 등록 — 기존 카드에 산출물 단건 추가 (생성 시 함께 등록하는 것과 별개 경로).
    @Transactional
    public TaskAttachmentAddResponse addAttachment(
            Long projectId, Long taskId, Long userId, TaskAttachmentAddRequest request) {
        // 1) 활성 멤버면 누구나 등록 가능
        projectAccessService.requireActiveMember(projectId, userId);

        // 2) 소속 검증까지 포함된 단건 조회
        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        // 3) EXTERNAL 타입 거부, FILE/LINK 검증
        if (request.attachmentType() == AttachmentType.EXTERNAL) {
            throw new ApiException(TaskErrorCode.INVALID_ATTACHMENT);
        }

        // 4) 여기서부터 "개수 확인 -> insert"를 원자적으로 묶는다.
        //    같은 taskId 행에 PESSIMISTIC_WRITE 락을 걸어, 동시에 들어온 두 요청이
        //    각자 "9개니까 통과"라고 잘못 판단해서 둘 다 insert하는 것(레이스 컨디션)을 막는다.
        //    먼저 도착한 트랜잭션이 커밋(또는 롤백)할 때까지 나머지 요청은 이 지점에서 대기한다.
        taskRepository.findByIdForUpdate(taskId);

        // 5) 전체 목록이 아니라 개수만 조회 (COUNT 쿼리 1번, 락이 걸린 상태라 이 값은 안전하게 최신값)
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

        // 6) 저장 — 락을 잡은 채로 insert까지 끝내고 트랜잭션 커밋 시점에 락 해제
        TaskAttachment attachment = TaskAttachment.create(
                task, request.attachmentType(), request.fileName(), request.fileSize(), storedValue);
        taskAttachmentRepository.save(attachment);
        if (request.attachmentType() == AttachmentType.FILE) {
            eventPublisher.publishEvent(new FilePromotionEvent(List.of(storedValue)));
        }

        return TaskAttachmentAddResponse.of(attachment, resolveUrl(attachment));
    }

    // 첨부파일 삭제
    @Transactional
    public TaskDeleteResponse deleteAttachment(
            Long projectId, Long taskId, Long taskAttachmentId, Long userId) {
        // 1) 활성 멤버면 누구나 삭제 가능
        projectAccessService.requireActiveMember(projectId, userId);

        // 2) taskId가 이 프로젝트 소속인지 먼저 확인
        taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        // 3) 삭제 대상 첨부파일이 실제로 그 taskId 소속인지 확인
        //    (다른 카드의 taskAttachmentId를 URL의 taskId에 끼워 넣는 것을 차단)
        TaskAttachment attachment = taskAttachmentRepository.findByIdAndTaskId(taskAttachmentId, taskId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_ATTACHMENT_NOT_FOUND));

        boolean isFile = attachment.getAttachmentType() == AttachmentType.FILE;
        String fileKey = attachment.getFileUrl();

        taskAttachmentRepository.delete(attachment);
        taskAttachmentRepository.flush();

        // 4) FILE 타입이면 S3 실물 파일도 커밋 후 비동기 삭제
        if (isFile) {
            eventPublisher.publishEvent(new FileDeletionEvent(List.of(fileKey)));
        }

        return new TaskDeleteResponse(true);
    }

    // 특정 프로젝트 멤버(담당자) 기준 업무카드 목록 조회
    @Transactional(readOnly = true)
    public TaskListResponse getTasksByMember(Long projectId, Long projectMemberId, Long userId) {
        // 1) 로그인 사용자가 해당 프로젝트의 활성 멤버인지 검증
        projectAccessService.requireActiveMember(projectId, userId);

        // 2) 조회 대상 멤버가 실제 이 프로젝트 소속이고, 활성 상태인지 검증
        ProjectMember targetMember = projectMemberRepository.findById(projectMemberId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.MEMBER_NOT_FOUND));
        if (!targetMember.getProject().getId().equals(projectId) || targetMember.getStatus() != MemberStatus.ACTIVE) {
            throw new ApiException(ProjectErrorCode.MEMBER_NOT_FOUND);
        }


        // 3) 목록 조회와 동일한 방식으로 EntityGraph + count 집계 재사용
        List<Task> tasks = taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(projectMemberId);
        if (tasks.isEmpty()) {
            return TaskListResponse.of(List.of());
        }

        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        Map<Long, Long> attachmentCountByTaskId = taskAttachmentRepository.countByTaskIds(taskIds).stream()
                .collect(Collectors.toMap(TaskAttachmentCount::getTaskId, TaskAttachmentCount::getCount));

        List<TaskSummaryResponse> content = tasks.stream()
                .map(task -> TaskSummaryResponse.from(
                        task,
                        attachmentCountByTaskId.getOrDefault(task.getId(), 0L).intValue()))
                .toList();

        return TaskListResponse.of(content);
    }
}