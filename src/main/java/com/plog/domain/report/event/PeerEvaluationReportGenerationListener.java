package com.plog.domain.report.event;

import com.plog.domain.evaluation.event.PeerEvaluationSubmittedEvent;
import com.plog.domain.report.service.ReportAutomaticGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PeerEvaluationReportGenerationListener {

    private final ReportAutomaticGenerationService automaticGenerationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(PeerEvaluationSubmittedEvent event) {
        try {
            automaticGenerationService.generateIfEvaluationCompleted(event.evaluateeId());
        } catch (RuntimeException exception) {
            // Peer 저장은 이미 커밋됐다. 자동 생성 실패가 평가 제출 성공을 되돌리면 안 된다.
            log.error("peer_evaluation_report_generation_failed evaluationId={}", event.evaluationId(), exception);
        }
    }
}
