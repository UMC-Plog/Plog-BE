package com.plog.domain.chat.service;

import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSubscriptionInterceptor implements ChannelInterceptor {

    private static final String CHAT_ROOM_TOPIC_PREFIX = "/topic/chat-rooms/";

    private final ChatRoomRepository chatRoomRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorize(accessor);
        }
        return message;
    }

    private void authorize(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(CHAT_ROOM_TOPIC_PREFIX)) {
            return;
        }
        Long userId = extractUserId(accessor);
        Long chatRoomId = parseChatRoomId(destination);
        chatRoomRepository.findAccessibleRoom(chatRoomId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));
    }

    private Long extractUserId(StompHeaderAccessor accessor) {
        Object userId = accessor.getSessionAttributes() == null
                ? null : accessor.getSessionAttributes().get("userId");
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        return (Long) userId;
    }

    /**
     * 접두사 뒤 <b>첫 세그먼트만</b> 방 ID 로 읽는다. 전체를 파싱하면
     * /topic/chat-rooms/12/attachments 같은 하위 목적지가 NumberFormatException 으로
     * 막힌다(썸네일 준비 push 가 이 경로를 쓴다).
     * <p>
     * 파싱이 느슨해진 대가로 /topic/chat-rooms/12/오타 도 통과한다. 방 멤버십 검사는
     * 그대로 걸리므로 보안 구멍은 아니지만, 잘못된 목적지 구독이 조용히 성공한다.
     */
    private Long parseChatRoomId(String destination) {
        String remainder = destination.substring(CHAT_ROOM_TOPIC_PREFIX.length());
        int slash = remainder.indexOf('/');
        String roomId = slash < 0 ? remainder : remainder.substring(0, slash);
        try {
            return Long.valueOf(roomId);
        } catch (NumberFormatException exception) {
            throw new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);
        }
    }
}