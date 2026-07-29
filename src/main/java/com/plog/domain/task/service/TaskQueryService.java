package com.plog.domain.task.service;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.response.TaskDetailResponse;
import com.plog.domain.task.dto.response.TaskListResponse;
import com.plog.domain.task.dto.response.TaskSummaryResponse;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskAttachmentRepository.TaskAttachmentCount;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.global.api.exception.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 업무카드 조회 전용 — 목록/상세/멤버별/마감초과
@Service
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessService projectAccessService;
    private final TaskAttachmentUrlResolver urlResolver;

    public TaskQueryService(TaskRepository taskRepository,
                            TaskAttachmentRepository taskAttachmentRepository,
                            ProjectMemberRepository projectMemberRepository,
                            ProjectAccessService projectAccessService,
                            TaskAttachmentUrlResolver urlResolver) {
        this.taskRepository = taskRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectAccessService = projectAccessService;
        this.urlResolver = urlResolver;
    }

    // 업무카드 목록 조회
    @Transactional(readOnly = true)
    public TaskListResponse getTaskList(Long projectId, Long userId) {
        projectAccessService.requireActiveMember(projectId, userId);
        List<Task> tasks = taskRepository.findAllByProjectMember_Project_IdOrderByCreatedAtAsc(projectId);
        return buildTaskListResponse(tasks);
    }

    // 업무카드 상세 조회
    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskDetail(Long projectId, Long taskId, Long userId) {
        projectAccessService.requireActiveMember(projectId, userId);

        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        List<TaskDetailResponse.AttachmentResponse> attachments = taskAttachmentRepository
                .findAllByTaskId(taskId).stream()
                .map(attachment -> TaskDetailResponse.AttachmentResponse.of(
                        attachment, urlResolver.resolveDownloadUrlApi(projectId, attachment)))
                .toList();

        return TaskDetailResponse.from(task, attachments);
    }

    // 특정 프로젝트 멤버(담당자) 기준 업무카드 목록 조회
    @Transactional(readOnly = true)
    public TaskListResponse getTasksByMember(Long projectId, Long projectMemberId, Long userId) {
        projectAccessService.requireActiveMember(projectId, userId);

        ProjectMember targetMember = projectMemberRepository.findById(projectMemberId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.MEMBER_NOT_FOUND));
        if (!targetMember.getProject().getId().equals(projectId) || targetMember.getStatus() != MemberStatus.ACTIVE) {
            throw new ApiException(ProjectErrorCode.MEMBER_NOT_FOUND);
        }

        List<Task> tasks = taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(projectMemberId);
        return buildTaskListResponse(tasks);
    }

    // 마감일 초과 업무카드 조회
    @Transactional(readOnly = true)
    public TaskListResponse getOverdueTasks(Long projectId, Long userId) {
        projectAccessService.requireActiveMember(projectId, userId);

        List<Task> tasks = taskRepository.findOverdueTasksByProjectId(
                projectId, LocalDate.now(), TaskStatus.DONE);
        return buildTaskListResponse(tasks);
    }

    // 목록 조회 3곳(전체/멤버별/마감초과)에서 공통으로 쓰는 응답 조립
    private TaskListResponse buildTaskListResponse(List<Task> tasks) {
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