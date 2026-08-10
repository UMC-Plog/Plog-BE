package com.plog.domain.report.repository.projection;

import java.time.LocalDateTime;

public interface PostLogRecoveryTarget {
    Long getPostId();
    Long getMemberId();
    String getContent();
    LocalDateTime getOccurredAt();
}
