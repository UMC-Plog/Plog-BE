package com.plog.domain.post.event;

import java.time.LocalDateTime;

public record PostCreatedEvent(
        Long postId,
        Long projectMemberId,
        String content,
        LocalDateTime occurredAt
) {
}
