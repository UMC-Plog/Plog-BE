package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatMessageResponse;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private static final String CHAT_ROOM_TOPIC_PREFIX = "/topic/chat-rooms/";

    private final ChatMessageAppender chatMessageAppender;
    private final SimpMessagingTemplate messagingTemplate;

    public void send(Long roomId, Long userId, String message) {
        if (message == null || message.isBlank()) {
            throw new ApiException(ChatErrorCode.EMPTY_MESSAGE_CONTENT);
        }

        ChatMessage chatMessage = chatMessageAppender.appendByUser(roomId, userId, message);
        messagingTemplate.convertAndSend(
                CHAT_ROOM_TOPIC_PREFIX + roomId,
                toResponse(chatMessage)
        );
    }

    private ChatMessageResponse toResponse(ChatMessage chatMessage) {
        ProjectMember sender = chatMessage.getProjectMember();
        return new ChatMessageResponse(
                chatMessage.getId(),
                chatMessage.getChatRoom().getId(),
                chatMessage.getMessageSequence(),
                sender.getId(),
                sender.getAnNickname(),
                sender.getUser().getProfileImageUrl(),
                chatMessage.getMessage(),
                TimeUtil.toInstant(chatMessage.getCreatedAt())
        );
    }
}