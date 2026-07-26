package com.plog.domain.chat.dto.response;

public record ChatReadResponse(
        Long roomId,
        Long lastReadMessageSequence,
        long unreadMessageCount
) {
}