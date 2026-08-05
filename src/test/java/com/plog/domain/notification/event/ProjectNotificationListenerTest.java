package com.plog.domain.notification.event;

import static org.mockito.Mockito.verify;

import com.plog.domain.notification.service.ProjectNotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class ProjectNotificationListenerTest {
    @Mock private ProjectNotificationService notificationService;

    @Test
    void 담당_알림_이벤트를_각_발송_메서드로_전달한다() {
        ProjectNotificationListener listener = new ProjectNotificationListener(notificationService);
        ChatMessageNotificationEvent chat =
                new ChatMessageNotificationEvent(1L, 2L, 3L, 4L, List.of(5L), "메시지");
        PeerEvaluationStartedEvent peer = new PeerEvaluationStartedEvent(1L, 4L);
        ReportPublishedEvent report = new ReportPublishedEvent(1L, 6L);

        listener.onChatMessage(chat);
        listener.onPeerEvaluationStarted(peer);
        listener.onReportPublished(report);

        verify(notificationService).sendChatMessage(chat);
        verify(notificationService).sendPeerEvaluationStarted(peer);
        verify(notificationService).sendReportPublished(report);
    }

    @Test
    void 원본_트랜잭션_커밋_후에만_알림을_발송한다() throws NoSuchMethodException {
        assertAfterCommit("onChatMessage", ChatMessageNotificationEvent.class);
        assertAfterCommit("onPeerEvaluationStarted", PeerEvaluationStartedEvent.class);
        assertAfterCommit("onReportPublished", ReportPublishedEvent.class);
    }

    private void assertAfterCommit(String methodName, Class<?> eventType) throws NoSuchMethodException {
        TransactionalEventListener annotation = ProjectNotificationListener.class
                .getMethod(methodName, eventType)
                .getAnnotation(TransactionalEventListener.class);
        org.assertj.core.api.Assertions.assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
