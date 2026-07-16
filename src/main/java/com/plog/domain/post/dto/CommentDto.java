package com.plog.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class CommentDto {
    private CommentDto() {}

    public record CreateRequest(@NotBlank @Size(max = 1000) String content) {}

    public record Response(
            Long commentId,
            Long postId,
            Long projectId,
            Long projectMemberId,
            String authorNickname,
            String content,
            Instant createdAt
    ) {}

    public record ListResponse(Long postId, List<Response> comments) {}
}
