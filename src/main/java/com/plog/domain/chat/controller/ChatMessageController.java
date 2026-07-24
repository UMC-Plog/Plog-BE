package com.plog.domain.chat.controller;

import com.plog.domain.chat.dto.request.ChatMessageSendRequest;
import com.plog.domain.chat.dto.response.ChatErrorResponse;
import com.plog.domain.chat.service.ChatMessageSendService;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.websocket.StompPrincipal;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageSendService chatMessageSendService;

    @MessageMapping("/chat-rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload ChatMessageSendRequest request,
            Principal principal
    ) {
        Long userId = ((StompPrincipal) principal).userId();
        chatMessageSendService.send(roomId, userId, request.clientMessageId(), request.message());
    }

    // 발신자 본인에게만 전송 — /user/queue/errors 구독 필요 (클라이언트가 세션별 개인 큐 구독)
    @MessageExceptionHandler(ApiException.class)
    @SendToUser("/queue/errors")
    public ChatErrorResponse handleApiException(ApiException exception) {
        return new ChatErrorResponse(
                exception.getErrorCode().getCode(),
                exception.getErrorCode().getMessage()
        );
    }
}