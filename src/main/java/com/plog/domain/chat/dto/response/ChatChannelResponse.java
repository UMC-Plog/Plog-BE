package com.plog.domain.chat.dto.response;

import java.time.Instant;
import java.util.List;

public record ChatChannelResponse(
        Long projectId,
        String projectName,
        Long roomId,
        String latestMessage,
        Instant latestMessageAt,
        boolean hasUnreadMessage,
        long unreadMessageCount,
        List<ChatChannelParticipantResponse> participants
) {
}
