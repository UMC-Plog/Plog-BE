package com.plog.e2e.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.plog.domain.notification.event.ChatMentionEvent;
import com.plog.e2e.support.E2eTestBase;
import com.plog.infrastructure.fcm.FcmDeliveryException;
import com.plog.infrastructure.fcm.FcmMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("ChatMentionNotification E2E")
class ChatMentionNotificationE2eTest extends E2eTestBase {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Nested
    @DisplayName("AFTER_COMMIT ChatMentionEvent")
    class CommittedChatMentionEvent {

        @Test
        @DisplayName("유효한 멘션 대상에게 한 번만 FCM 알림을 발송한다")
        void sendsOnceAndFiltersInvalidTargets() {
            Long senderUserId = saveUser("mention-sender");
            Long targetUserId = saveUser("mention-target");
            Long exitedUserId = saveUser("mention-exited");
            Long projectId = saveProject("mention");
            Long senderMemberId = saveMember(senderUserId, projectId, "MEMBER", "ACTIVE", "곰곰");
            Long targetMemberId = saveMember(targetUserId, projectId, "MEMBER", "ACTIVE", "포도");
            Long exitedMemberId = saveMember(exitedUserId, projectId, "MEMBER", "EXIT", "사과");
            saveFcm(targetUserId, "target-token");
            saveFcm(exitedUserId, "exited-token");

            publishAfterCommit(new ChatMentionEvent(
                    projectId,
                    77L,
                    senderMemberId,
                    List.of(targetMemberId, targetMemberId, senderMemberId, exitedMemberId),
                    " 확인 부탁해요 "
            ));

            ArgumentCaptor<FcmMessage> captor = ArgumentCaptor.forClass(FcmMessage.class);
            verify(fcmGateway, timeout(3_000).times(1)).send(captor.capture());
            FcmMessage message = captor.getValue();
            assertThat(message.token()).isEqualTo("target-token");
            assertThat(message.title()).isEqualTo("Plog mention 멘션");
            assertThat(message.body()).isEqualTo("곰곰님: 확인 부탁해요");
            assertThat(message.data())
                    .containsEntry("projectId", projectId.toString())
                    .containsEntry("chatId", "77")
                    .containsEntry("type", "CHAT_MENTION");
        }

        @Test
        @DisplayName("유효하지 않은 FCM 토큰은 물리 삭제한다")
        void deletesInvalidToken() {
            Long senderUserId = saveUser("invalid-sender");
            Long targetUserId = saveUser("invalid-target");
            Long projectId = saveProject("invalid-token");
            Long senderMemberId = saveMember(senderUserId, projectId, "MEMBER", "ACTIVE", "발신자");
            Long targetMemberId = saveMember(targetUserId, projectId, "MEMBER", "ACTIVE", "대상");
            saveFcm(targetUserId, "invalid-token");
            doThrow(new FcmDeliveryException(true, new RuntimeException("unregistered")))
                    .when(fcmGateway).send(any(FcmMessage.class));

            publishAfterCommit(new ChatMentionEvent(
                    projectId, 88L, senderMemberId, List.of(targetMemberId), "hello"));

            verify(fcmGateway, timeout(3_000).times(1)).send(any(FcmMessage.class));
            awaitTokenDeletion("invalid-token");
            assertThat(fcmCount("invalid-token")).isZero();
        }
    }

    private void saveFcm(Long userId, String token) {
        jdbc.update("""
                insert into fcm (user_id, token, created_at, updated_at)
                values (?, ?, now(), now())
                """, userId, token);
    }

    private void publishAfterCommit(ChatMentionEvent event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(event));
    }

    private void awaitTokenDeletion(String token) {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (fcmCount(token) != 0L && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }

    private long fcmCount(String token) {
        return jdbc.queryForObject(
                "select count(*) from fcm where token = ?", Long.class, token);
    }
}
