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

    private Long parseChatRoomId(String destination) {
        try {
            return Long.valueOf(destination.substring(CHAT_ROOM_TOPIC_PREFIX.length()));
        } catch (NumberFormatException exception) {
            throw new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);
        }
    }
}