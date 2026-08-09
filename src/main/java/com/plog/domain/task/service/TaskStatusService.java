package com.plog.domain.task.service;

import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.request.TaskStatusUpdateRequest;
import com.plog.domain.task.dto.response.TaskStatusUpdateResponse;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.event.TaskStatusChangedEvent;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 업무카드 상태 변경
// 별도 분리한 이유: report 파이프라인(activity_log 수집)에서 상태 변경 이벤트를 훅해야 할 가능성이 높아서,
// 생성/수정 로직과 뒤섞이지 않게 미리 분리해둠.
@Service
public class TaskStatusService {

    private final TaskRepository taskRepository;
    private final ProjectAccessService projectAccessService;
    private final ApplicationEventPublisher eventPublisher;

    public TaskStatusService(
            TaskRepository taskRepository,
            ProjectAccessService projectAccessService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.taskRepository = taskRepository;
        this.projectAccessService = projectAccessService;
        this.eventPublisher = eventPublisher;
    }

    // 업무카드 상태 변경 전용 API
    @Transactional
    public TaskStatusUpdateResponse updateTaskStatus(
            Long projectId, Long taskId, Long userId, TaskStatusUpdateRequest request) {
        projectAccessService.requireActiveMember(projectId, userId);

        Task task = taskRepository.findByIdAndProjectMember_Project_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        TaskStatus previousStatus = task.getCardStatus();
        task.changeStatus(request.cardStatus());

        // 같은 상태로의 PATCH는 "활동"으로 볼 근거가 없어 report 파이프라인에 신호를 보내지 않는다.
        if (previousStatus != request.cardStatus()) {
            eventPublisher.publishEvent(new TaskStatusChangedEvent(
                    task.getId(), task.getProjectMember().getId(), previousStatus, request.cardStatus(),
                    TimeUtil.nowUtc()));
        }

        return TaskStatusUpdateResponse.from(task);
    }
}