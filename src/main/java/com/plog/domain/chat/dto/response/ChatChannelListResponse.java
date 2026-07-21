package com.plog.domain.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatChannelListResponse(
        List<Channel> content,
        PageInfo pageInfo
) {

    public record Channel(
            Long projectId,
            String projectName,
            Long roomId,
            String latestMessage,
            LocalDateTime latestMessageAt,
            boolean hasUnreadMessage,
            long unreadMessageCount
    ) {
    }

    public record PageInfo(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
