package com.plog.domain.report.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.report.repository.projection.TaskAttachmentLogRecoveryTarget;
import com.plog.domain.report.repository.projection.TaskStatusLogRecoveryTarget;
import com.plog.domain.report.service.TaskActivityLogService;
import com.plog.domain.task.entity.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskActivityLogRecoverySchedulerTest {
    @Mock private ReportActivityLogRepository activityLogRepository;
    @Mock private TaskActivityLogService activityLogService;

    private TaskActivityLogRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TaskActivityLogRecoveryScheduler(activityLogRepository, activityLogService);
    }

    @Test
    void 유실된_상태변경과_첨부를_각각_수집_서비스에_위임한다() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        when(activityLogRepository.findDoneTasksMissingActivityLog(any(), any()))
                .thenReturn(List.of(target(1L, 7L, occurredAt)));
        when(activityLogRepository.findAttachmentsMissingActivityLog(any(), any()))
                .thenReturn(List.of(attachmentTarget(9L, 1L, 7L, occurredAt)));

        scheduler.recollectMissing();

        verify(activityLogService).collectStatusChanged(1L, 7L, null, TaskStatus.DONE, occurredAt);
        verify(activityLogService).collectAttachmentAdded(9L, 1L, 7L, occurredAt);
    }

    @Test
    void 한_건이_실패해도_나머지_건은_계속_처리한다() {
        LocalDateTime occurredAt = LocalDateTime.now();
        when(activityLogRepository.findDoneTasksMissingActivityLog(any(), any()))
                .thenReturn(List.of(target(1L, 7L, occurredAt), target(2L, 8L, occurredAt)));
        when(activityLogRepository.findAttachmentsMissingActivityLog(any(), any()))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("boom"))
                .when(activityLogService).collectStatusChanged(1L, 7L, null, TaskStatus.DONE, occurredAt);

        scheduler.recollectMissing();

        verify(activityLogService).collectStatusChanged(2L, 8L, null, TaskStatus.DONE, occurredAt);
    }

    private TaskStatusLogRecoveryTarget target(Long taskId, Long memberId, LocalDateTime occurredAt) {
        return new TaskStatusLogRecoveryTarget() {
            public Long getTaskId() {
                return taskId;
            }

            public Long getMemberId() {
                return memberId;
            }

            public LocalDateTime getOccurredAt() {
                return occurredAt;
            }
        };
    }

    private TaskAttachmentLogRecoveryTarget attachmentTarget(
            Long attachmentId, Long taskId, Long memberId, LocalDateTime occurredAt) {
        return new TaskAttachmentLogRecoveryTarget() {
            public Long getAttachmentId() {
                return attachmentId;
            }

            public Long getTaskId() {
                return taskId;
            }

            public Long getMemberId() {
                return memberId;
            }

            public LocalDateTime getOccurredAt() {
                return occurredAt;
            }
        };
    }
}