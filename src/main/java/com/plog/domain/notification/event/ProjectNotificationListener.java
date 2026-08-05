package com.plog.domain.notification.event;

import com.plog.domain.notification.service.ProjectNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProjectNotificationListener {
    private final ProjectNotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessage(ChatMessageNotificationEvent event) {
        notificationService.sendChatMessage(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPeerEvaluationStarted(PeerEvaluationStartedEvent event) {
        notificationService.sendPeerEvaluationStarted(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportPublished(ReportPublishedEvent event) {
        notificationService.sendReportPublished(event);
    }
}
