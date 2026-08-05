package com.plog.domain.report.event;

import com.plog.domain.evaluation.event.PeerEvaluationSubmittedEvent;
import com.plog.domain.evaluation.event.SelfFeedbackSubmittedEvent;
import com.plog.domain.report.service.EvaluationActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EvaluationActivityLogListener {
    private final EvaluationActivityLogService activityLogService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPeerEvaluationSubmitted(PeerEvaluationSubmittedEvent event) {
        activityLogService.collectPeerEvaluation(event.evaluationId(), event.occurredAt());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSelfFeedbackSubmitted(SelfFeedbackSubmittedEvent event) {
        activityLogService.collectSelfFeedback(event.selfFeedbackId(), event.occurredAt());
    }
}
