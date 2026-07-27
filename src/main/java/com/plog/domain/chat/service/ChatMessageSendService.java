package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.request.ChatMessageSendRequest;
import com.plog.domain.chat.dto.request.ChatMessageSendRequest.ChatMessageAttachmentRequest;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private static final int MAX_CLIENT_MESSAGE_ID_LENGTH = 64;

    private final ChatMessageAppender chatMessageAppender;

    public void send(
            Long roomId,
            Long userId,
            String clientMessageId,
            String message,
            List<ChatMessageAttachmentRequest> attachments
    ) {
        List<ChatMessageSendRequest.ChatMessageAttachmentRequest> safeAttachments = attachments == null ? List.of() : attachments;

        // 텍스트만/첨부만/둘 다 허용 - 둘 다 없을 때만 막음
        if ((message == null || message.isBlank()) && safeAttachments.isEmpty()) {
            throw new ApiException(ChatErrorCode.EMPTY_MESSAGE_CONTENT);
        }
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw new ApiException(ChatErrorCode.MISSING_CLIENT_MESSAGE_ID);
        }
        if (clientMessageId.length() > MAX_CLIENT_MESSAGE_ID_LENGTH) {
            throw new ApiException(ChatErrorCode.INVALID_CLIENT_MESSAGE_ID);
        }

        // 첨부 검증은 appender 안쪽(멱등 히트 판정 이후)에서 한다. 여기서 하면
        // 재전송 시 이미 CONFIRMED 인 첨부를 다시 확정하려다 409 로 죽는다.
        chatMessageAppender.appendByUser(roomId, userId, clientMessageId, message, safeAttachments);
    }
}