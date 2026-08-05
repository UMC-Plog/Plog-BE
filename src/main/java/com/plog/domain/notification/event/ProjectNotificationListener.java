package com.plog.domain.notification.event;

import com.plog.domain.notification.service.ProjectNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectNotificationListener {
    private final ProjectNotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessage(ChatMessageNotificationEvent event) {
        try {
            notificationService.sendChatMessage(event);
        } catch (RuntimeException exception) {
            log.error("chat_message_notification_failed projectId={} roomId={} chatId={}",
                    event.projectId(), event.roomId(), event.chatId(), exception);
            throw exception;
        }
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
