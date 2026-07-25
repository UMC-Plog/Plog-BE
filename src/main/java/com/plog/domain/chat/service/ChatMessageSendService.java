package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.request.ChatMessageSendRequest;
import com.plog.domain.chat.dto.request.ChatMessageSendRequest.ChatMessageAttachmentRequest;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.AttachmentUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private static final int MAX_CLIENT_MESSAGE_ID_LENGTH = 64;

    private final ChatMessageAppender chatMessageAppender;
    private final AttachmentPolicy attachmentPolicy;

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

        validateAttachments(userId, safeAttachments);

        chatMessageAppender.appendByUser(roomId, userId, clientMessageId, message, safeAttachments);
    }

    private void validateAttachments(Long userId, List<ChatMessageAttachmentRequest> attachments) {
        attachmentPolicy.validateCount(attachments.size(), ChatErrorCode.TOO_MANY_CHAT_ATTACHMENTS);
        for (ChatMessageAttachmentRequest attachment : attachments) {
            if (attachment == null
                    || attachment.fileKey() == null || attachment.fileKey().isBlank()
                    || attachment.fileName() == null || attachment.fileName().isBlank()
                    || attachment.fileSize() == null) {
                throw new ApiException(ChatErrorCode.INVALID_CHAT_ATTACHMENT);
            }
            // S3에 실제로 업로드된 파일인지, 용도(CHAT)·소유자·크기·확장자·MIME까지 대조한다.
            // (허용되지 않은 확장자 / 크기 초과 / 임의 URL 첨부는 모두 여기서 걸러짐)
            attachmentPolicy.validateFileAttachment(
                    AttachmentUsage.CHAT, userId,
                    attachment.fileName(), attachment.fileSize(), attachment.fileKey(),
                    ChatErrorCode.INVALID_CHAT_ATTACHMENT);
        }
    }
}