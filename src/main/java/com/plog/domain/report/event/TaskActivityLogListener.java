package com.plog.domain.report.event;

import com.plog.domain.report.service.TaskActivityLogService;
import com.plog.domain.task.event.TaskAttachmentAddedEvent;
import com.plog.domain.task.event.TaskStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "plog.report.activity.realtime-collection.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TaskActivityLogListener {
    private final TaskActivityLogService activityLogService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        activityLogService.collectStatusChanged(
                event.taskId(), event.projectMemberId(), event.previousStatus(), event.newStatus(),
                event.occurredAt());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAttachmentAdded(TaskAttachmentAddedEvent event) {
        activityLogService.collectAttachmentAdded(
                event.attachmentId(), event.taskId(), event.projectMemberId(), event.occurredAt());
    }
}
