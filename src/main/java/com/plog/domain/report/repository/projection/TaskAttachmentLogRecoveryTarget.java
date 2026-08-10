package com.plog.domain.report.repository.projection;

import java.time.LocalDateTime;

/** TASK_ATTACHMENT_ADD 재수집 대상. */
public interface TaskAttachmentLogRecoveryTarget {
    Long getAttachmentId();

    Long getTaskId();

    Long getMemberId();

    LocalDateTime getOccurredAt();
}