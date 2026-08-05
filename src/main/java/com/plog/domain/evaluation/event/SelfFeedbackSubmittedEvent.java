package com.plog.domain.evaluation.event;

import java.time.LocalDateTime;

public record SelfFeedbackSubmittedEvent(
        Long selfFeedbackId,
        Long projectMemberId,
        LocalDateTime occurredAt
) {
}
