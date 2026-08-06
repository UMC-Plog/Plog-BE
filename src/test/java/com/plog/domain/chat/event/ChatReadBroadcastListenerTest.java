package com.plog.domain.chat.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.chat.dto.response.ChatReadUpdateMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatReadBroadcastListenerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long USER_ID = 100L;
    private static final String CHAT_UPDATE_QUEUE = "/queue/chat-update";

    @Mock private SimpMessagingTemplate messagingTemplate;

    @Test
    void 읽음_처리_이벤트를_받으면_본인_큐로_push한다() {
        ChatReadBroadcastListener listener = new ChatReadBroadcastListener(messagingTemplate);

        listener.onChatReadUpdated(new ChatReadUpdatedEvent(ROOM_ID, USER_ID, 5L, 3L));

        ArgumentCaptor<ChatReadUpdateMessage> captor = ArgumentCaptor.forClass(ChatReadUpdateMessage.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(String.valueOf(USER_ID)), eq(CHAT_UPDATE_QUEUE), captor.capture());

        ChatReadUpdateMessage payload = captor.getValue();
        assertThat(payload.type()).isEqualTo("READ_UPDATE");
        assertThat(payload.roomId()).isEqualTo(ROOM_ID);
        assertThat(payload.lastReadMessageSequence()).isEqualTo(5L);
        assertThat(payload.unreadMessageCount()).isEqualTo(3L);
    }

    @Test
    void push가_일시적으로_실패해도_재시도해서_결국_전송한다() {
        ChatReadBroadcastListener listener = new ChatReadBroadcastListener(messagingTemplate);

        doThrow(new MessageDeliveryException("일시적 실패"))
                .doThrow(new MessageDeliveryException("일시적 실패"))
                .doNothing()
                .when(messagingTemplate).convertAndSendToUser(any(), any(), any());

        listener.onChatReadUpdated(new ChatReadUpdatedEvent(ROOM_ID, USER_ID, 5L, 3L));

        verify(messagingTemplate, times(3)).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void 재시도를_모두_소진하면_예외_없이_포기한다() {
        ChatReadBroadcastListener listener = new ChatReadBroadcastListener(messagingTemplate);

        doThrow(new MessageDeliveryException("영구 실패"))
                .when(messagingTemplate).convertAndSendToUser(any(), any(), any());

        listener.onChatReadUpdated(new ChatReadUpdatedEvent(ROOM_ID, USER_ID, 5L, 3L));

        // MAX_RETRIES(3) + 최초 시도 = 총 4회 호출 후 예외를 던지지 않고 종료한다
        verify(messagingTemplate, times(4)).convertAndSendToUser(any(), any(), any());
    }
}