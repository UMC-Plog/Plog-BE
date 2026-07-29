package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.global.api.exception.ApiException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class ChatSubscriptionInterceptorTest {

    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final MessageChannel channel = mock(MessageChannel.class);

    private final ChatSubscriptionInterceptor interceptor =
            new ChatSubscriptionInterceptor(chatRoomRepository);

    @Test
    void 메시지_토픽_구독은_기존대로_동작한다() {
        ChatRoom room = mock(ChatRoom.class);
        given(chatRoomRepository.findAccessibleRoom(eq(12L), eq(7L), any()))
                .willReturn(Optional.of(room));

        interceptor.preSend(subscribe("/topic/chat-rooms/12", 7L), channel);

        verify(chatRoomRepository).findAccessibleRoom(eq(12L), eq(7L), any());
    }

    /**
     * 썸네일 준비 push 가 쓰는 경로. 파싱이 접두사 뒤 전체를 Long 으로 읽으면
     * NumberFormatException 으로 403 이 되어 구독 자체가 막힌다.
     */
    @Test
    void 첨부_하위_경로_구독도_방_권한으로_검사한다() {
        ChatRoom room = mock(ChatRoom.class);
        given(chatRoomRepository.findAccessibleRoom(eq(12L), eq(7L), any()))
                .willReturn(Optional.of(room));

        interceptor.preSend(subscribe("/topic/chat-rooms/12/attachments", 7L), channel);

        verify(chatRoomRepository).findAccessibleRoom(eq(12L), eq(7L), any());
    }

    /** 파싱을 느슨하게 해도 멤버십 검사는 그대로 걸려야 한다. */
    @Test
    void 첨부_하위_경로도_비멤버는_막힌다() {
        given(chatRoomRepository.findAccessibleRoom(eq(12L), eq(9L), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                interceptor.preSend(subscribe("/topic/chat-rooms/12/attachments", 9L), channel))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 방_ID가_숫자가_아니면_막힌다() {
        assertThatThrownBy(() ->
                interceptor.preSend(subscribe("/topic/chat-rooms/abc/attachments", 7L), channel))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 채팅방_토픽이_아니면_검사하지_않는다() {
        interceptor.preSend(subscribe("/topic/other", 7L), channel);

        verify(chatRoomRepository, never()).findAccessibleRoom(any(), any(), any());
    }

    private Message<byte[]> subscribe(String destination, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(new HashMap<>(Map.of("userId", userId)));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
