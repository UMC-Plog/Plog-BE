package com.plog.domain.post.event;

import java.time.LocalDateTime;

public record CommentCreatedEvent(
        Long commentId,
        Long postId,
        Long projectMemberId,
        String content,
        LocalDateTime occurredAt
) {
}
