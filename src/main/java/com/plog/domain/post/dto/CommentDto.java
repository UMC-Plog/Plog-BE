package com.plog.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;

public final class CommentDto {
    private CommentDto() {}

    @Schema(name = "PostCommentCreateRequest")
    public record CreateRequest(@NotBlank @Size(max = 1000) String content) {}

    @Schema(name = "PostCommentResponse")
    public record Response(
            Long commentId,
            Long postId,
            Long projectId,
            Long projectMemberId,
            String authorNickname,
            ProfilePreset profilePreset,
            String content,
            Instant createdAt
    ) {}

    @Schema(name = "PostCommentListResponse")
    public record ListResponse(Long postId, List<Response> comments) {}
}
