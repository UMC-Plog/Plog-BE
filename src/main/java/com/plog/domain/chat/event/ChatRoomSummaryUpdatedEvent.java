package com.plog.domain.chat.event;

import java.util.List;

public record ChatRoomSummaryUpdatedEvent(
        Long roomId,
        String latestMessage,
        List<Long> targetUserIds
) {
    public ChatRoomSummaryUpdatedEvent {
        targetUserIds = targetUserIds == null ? List.of() : List.copyOf(targetUserIds);
    }
}