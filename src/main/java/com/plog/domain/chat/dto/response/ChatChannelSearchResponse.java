package com.plog.domain.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatChannelSearchResponse(
        List<Channel> content,
        PageInfo pageInfo
) {

    public record Channel(
            Long projectId,
            String projectName,
            Long roomId,
            LocalDateTime latestMessageAt,
            boolean hasUnreadMessage
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
