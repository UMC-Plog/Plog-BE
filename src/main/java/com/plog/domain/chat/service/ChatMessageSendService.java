package com.plog.domain.chat.service;

import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private final ChatMessageAppender chatMessageAppender;

    public void send(Long roomId, Long userId, String clientMessageId, String message) {
        if (message == null || message.isBlank()) {
            throw new ApiException(ChatErrorCode.EMPTY_MESSAGE_CONTENT);
        }
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw new ApiException(ChatErrorCode.MISSING_CLIENT_MESSAGE_ID);
        }
        chatMessageAppender.appendByUser(roomId, userId, clientMessageId, message);
    }
}