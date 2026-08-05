package com.plog.e2e.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.plog.domain.chat.service.ChatMessageSendService;
import com.plog.e2e.support.E2eTestBase;
import com.plog.infrastructure.fcm.FcmMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("ChatMessageNotification E2E")
class ChatMessageNotificationE2eTest extends E2eTestBase {

    @Autowired
    private ChatMessageSendService chatMessageSendService;

    @Test
    @DisplayName("신규 일반 메시지는 수신자의 인앱 알림을 저장하고 FCM을 발송한다")
    void sendsChatMessageNotificationFromStoredMessage() {
        Long senderUserId = saveUser("chat-message-sender");
        Long targetUserId = saveUser("chat-message-target");
        Long projectId = saveProject("chat-message");
        saveMember(senderUserId, projectId, "MEMBER", "ACTIVE", "발신자");
        saveMember(targetUserId, projectId, "MEMBER", "ACTIVE", "수신자");
        Long roomId = jdbc.queryForObject("""
                insert into chat_rooms (project_id, last_message_sequence, created_at, updated_at)
                values (?, 0, now(), now())
                returning chat_room_id
                """, Long.class, projectId);
        jdbc.update("""
                insert into fcm (user_id, token, created_at, updated_at)
                values (?, 'chat-message-token', now(), now())
                """, targetUserId);

        chatMessageSendService.send(roomId, senderUserId, "chat-message-1", "안녕하세요", List.of());

        ArgumentCaptor<FcmMessage> captor = ArgumentCaptor.forClass(FcmMessage.class);
        verify(fcmGateway, timeout(3_000).times(1)).send(captor.capture());
        FcmMessage message = captor.getValue();
        assertThat(message.token()).isEqualTo("chat-message-token");
        assertThat(message.data())
                .containsEntry("type", "CHAT_MESSAGE")
                .containsEntry("projectId", projectId.toString())
                .containsEntry("roomId", roomId.toString());

        awaitChatMessageNotification(targetUserId);
        Long resourceId = jdbc.queryForObject("""
                select resource_id from notifications
                where user_id = ? and project_id = ? and type = 'CHAT_MESSAGE'
                """, Long.class, targetUserId, projectId);
        assertThat(message.data()).containsEntry("resourceId", resourceId.toString());
        assertThat(jdbc.queryForObject("""
                select count(*) from notifications
                where user_id = ? and project_id = ? and type = 'CHAT_MESSAGE'
                """, Long.class, targetUserId, projectId)).isEqualTo(1L);
    }

    private void awaitChatMessageNotification(Long userId) {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (notificationCount(userId) == 0L && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }

    private long notificationCount(Long userId) {
        Long count = jdbc.queryForObject(
                "select count(*) from notifications where user_id = ? and type = 'CHAT_MESSAGE'",
                Long.class,
                userId);
        return count == null ? 0L : count;
    }
}
