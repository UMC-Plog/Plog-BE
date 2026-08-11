package com.plog.domain.project.event;

import com.plog.domain.project.service.ProjectStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EvaluationCompletionCheckListener {

    private final ProjectStatusService projectStatusService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvaluationSubmitted(EvaluationCompletionCheckRequestedEvent event) {
        projectStatusService.checkAndComplete(event.projectId());
    }
}
