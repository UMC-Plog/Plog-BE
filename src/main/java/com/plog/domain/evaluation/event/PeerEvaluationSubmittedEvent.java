package com.plog.domain.evaluation.event;

import java.time.LocalDateTime;

public record PeerEvaluationSubmittedEvent(
        Long evaluationId,
        Long evaluatorId,
        Long evaluateeId,
        LocalDateTime occurredAt
) {
}
