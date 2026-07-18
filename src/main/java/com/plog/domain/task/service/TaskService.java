package com.plog.domain.task.service;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.dto.response.TaskCreateResponse;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.domain.task.exception.code.TaskErrorCode;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;

    public TaskService(TaskRepository taskRepository,
                       TaskAttachmentRepository taskAttachmentRepository,
                       ProjectMemberRepository projectMemberRepository,
                       ProjectAccessService projectAccessService) {
        this.taskRepository = taskRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectAccessService = projectAccessService;
    }

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
        List<TaskAttachment> attachments = createAttachments(task, request.attachments());

        return TaskCreateResponse.from(task, attachments);
    }

    private List<TaskAttachment> createAttachments(
            Task task, List<TaskCreateRequest.TaskAttachmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<TaskAttachment> attachments = requests.stream()
                .map(r -> TaskAttachment.create(task, r.attachmentType(), r.fileName(), r.fileSize(), r.fileUrl()))
                .toList();
        taskAttachmentRepository.saveAll(attachments);
        return attachments;
    }
}