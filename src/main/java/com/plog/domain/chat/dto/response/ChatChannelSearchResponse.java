package com.plog.domain.chat.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ChatChannelSearchResponse(
        List<SearchChannel> content,
        SearchPageInfo pageInfo
) {

    public record SearchChannel(
            Long projectId,
            String projectName,
            Long roomId,
            LocalDateTime latestMessageAt,
            boolean hasUnreadMessage
    ) {
    }

    public record SearchPageInfo(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
    }
}
