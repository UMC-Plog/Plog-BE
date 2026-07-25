package com.plog.domain.task.service;

import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.request.TaskStatusUpdateRequest;
import com.plog.domain.task.dto.response.TaskStatusUpdateResponse;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.global.api.exception.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 업무카드 상태 변경
// 별도 분리한 이유: report 파이프라인(activity_log 수집)에서 상태 변경 이벤트를 훅해야 할 가능성이 높아서,
// 생성/수정 로직과 뒤섞이지 않게 미리 분리해둠.
@Service
public class TaskStatusService {

    private final TaskRepository taskRepository;
    private final ProjectAccessService projectAccessService;

    public TaskStatusService(TaskRepository taskRepository, ProjectAccessService projectAccessService) {
        this.taskRepository = taskRepository;
        this.projectAccessService = projectAccessService;
    }

    // 업무카드 상태 변경 전용 API
    @Transactional
    public TaskStatusUpdateResponse updateTaskStatus(
            Long projectId, Long taskId, Long userId, TaskStatusUpdateRequest request) {
        projectAccessService.requireActiveMember(projectId, userId);

        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        task.changeStatus(request.cardStatus());

        return TaskStatusUpdateResponse.from(task);
    }
}