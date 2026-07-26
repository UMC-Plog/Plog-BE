package com.plog.domain.chat.dto.request;

import java.util.List;

public record ChatMessageSendRequest(
        String clientMessageId,
        String message,
        List<ChatMessageAttachmentRequest> attachments
) {

    public record ChatMessageAttachmentRequest(
            String fileKey,
            String fileName,
            Long fileSize
    ) {
    }
}