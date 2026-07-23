package com.plog.domain.chat.dto.response;

import java.time.Instant;

public record ChatMessageResponse(
        Long chatId,
        Long roomId,
        Long messageSequence,
        Long senderMemberId,
        String senderNickname,
        String senderProfileImageUrl,
        String message,
        Instant createdAt
) {
}