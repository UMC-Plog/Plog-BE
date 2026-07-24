package com.plog.domain.chat.dto.response;

import com.plog.domain.user.entity.ProfilePreset;

import java.time.Instant;

public record ChatMessageResponse(
        Long chatId,
        Long roomId,
        Long messageSequence,
        Long senderMemberId,
        String senderNickname,
        ProfilePreset profilePreset,
        String message,
        Instant createdAt
) {
}