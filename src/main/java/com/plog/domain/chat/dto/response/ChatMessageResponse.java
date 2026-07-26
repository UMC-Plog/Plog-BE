package com.plog.domain.chat.dto.response;

import com.plog.domain.user.entity.ProfilePreset;

import java.time.Instant;
import java.util.List;

public record ChatMessageResponse(
        Long chatId,
        Long roomId,
        Long messageSequence,
        Long senderMemberId,
        String senderNickname,
        ProfilePreset profilePreset,
        String message,
        List<ChatMessageAttachmentResponse> attachments,
        Instant createdAt
) {
    public record ChatMessageAttachmentResponse(
            Long chatAttachmentId,
            String fileName,
            Long fileSize,
            String fileUrl
    ) {
    }
}