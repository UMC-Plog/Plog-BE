package com.plog.domain.chat.event;

public record ChatReadUpdatedEvent(
        Long roomId,
        Long userId,
        Long lastReadMessageSequence,
        long unreadMessageCount
) {
}