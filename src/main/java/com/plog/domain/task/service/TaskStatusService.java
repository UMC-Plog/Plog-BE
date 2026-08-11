package com.plog.domain.task.service;

import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.request.TaskStatusUpdateRequest;
import com.plog.domain.task.dto.response.TaskStatusUpdateResponse;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.entity.TaskStatusHistory;
import com.plog.domain.task.event.TaskStatusChangedEvent;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.domain.task.repository.TaskStatusHistoryRepository;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import java.time.LocalDateTime;

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
    private final TaskStatusHistoryRepository taskStatusHistoryRepository;

    public TaskStatusService(
            TaskRepository taskRepository,
            ProjectAccessService projectAccessService,
            ApplicationEventPublisher eventPublisher,
            TaskStatusHistoryRepository taskStatusHistoryRepository
    ) {
        this.taskRepository = taskRepository;
        this.projectAccessService = projectAccessService;
        this.eventPublisher = eventPublisher;
        this.taskStatusHistoryRepository = taskStatusHistoryRepository;
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
            LocalDateTime occurredAt = occurredAt(task, request.cardStatus());
            taskStatusHistoryRepository.save(TaskStatusHistory.builder()
                    .task(task)
                    .projectMember(task.getProjectMember())
                    .previousStatus(previousStatus)
                    .newStatus(request.cardStatus())
                    .occurredAt(occurredAt)
                    .build());
            eventPublisher.publishEvent(new TaskStatusChangedEvent(
                    task.getId(), task.getProjectMember().getId(), previousStatus, request.cardStatus(),
                    occurredAt));
        }

        return TaskStatusUpdateResponse.from(task);
    }

    // DONE으로 바뀌는 경우엔 completedAt을 그대로 재사용한다 — 새로 TimeUtil.nowUtc()를 부르면
    // 두 값이 미세하게 어긋나서, TaskActivityLogRecoveryScheduler가 completedAt으로 재구성한
    // occurredAt과 정상 경로가 남긴 occurredAt이 일치하지 않아 안전망 조회가 무력화된다.
    // 그 외 전이는 Task에 저장해두는 시각이 없으니 지금 시각을 쓴다(재수집 대상에서는 제외됨).
    private LocalDateTime occurredAt(Task task, TaskStatus newStatus) {
        return newStatus == TaskStatus.DONE ? task.getCompletedAt() : TimeUtil.nowUtc();
    }
}
