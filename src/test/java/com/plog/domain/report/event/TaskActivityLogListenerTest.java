package com.plog.domain.report.event;

import static org.mockito.Mockito.verify;

import com.plog.domain.report.service.TaskActivityLogService;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.event.TaskAttachmentAddedEvent;
import com.plog.domain.task.event.TaskStatusChangedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class TaskActivityLogListenerTest {
    @Mock private TaskActivityLogService activityLogService;

    @Test
    void 상태변경과_첨부추가_이벤트를_수집_서비스에_전달한다() {
        TaskActivityLogListener listener = new TaskActivityLogListener(activityLogService);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 5, 12, 0);

        listener.onTaskStatusChanged(
                new TaskStatusChangedEvent(1L, 7L, TaskStatus.IN_PROGRESS, TaskStatus.DONE, occurredAt));
        listener.onTaskAttachmentAdded(new TaskAttachmentAddedEvent(9L, 1L, 7L, occurredAt));

        verify(activityLogService).collectStatusChanged(
                1L, 7L, TaskStatus.IN_PROGRESS, TaskStatus.DONE, occurredAt);
        verify(activityLogService).collectAttachmentAdded(9L, 1L, 7L, occurredAt);
    }

    @Test
    void 원본_트랜잭션_커밋_후에만_수집한다() throws NoSuchMethodException {
        assertAfterCommit("onTaskStatusChanged", TaskStatusChangedEvent.class);
        assertAfterCommit("onTaskAttachmentAdded", TaskAttachmentAddedEvent.class);
    }

    private void assertAfterCommit(String methodName, Class<?> eventType) throws NoSuchMethodException {
        TransactionalEventListener annotation = TaskActivityLogListener.class
                .getMethod(methodName, eventType)
                .getAnnotation(TransactionalEventListener.class);
        org.assertj.core.api.Assertions.assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}