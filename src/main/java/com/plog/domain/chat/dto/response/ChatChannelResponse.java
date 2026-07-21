package com.plog.domain.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatChannelResponse(
        Long projectId,
        String projectName,
        Long roomId,
        String latestMessage,
        LocalDateTime latestMessageAt,
        boolean hasUnreadMessage,
        long unreadMessageCount,
        List<ChatChannelParticipantResponse> participants
) {
}
