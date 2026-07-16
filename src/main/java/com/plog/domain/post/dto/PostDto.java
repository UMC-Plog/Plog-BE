package com.plog.domain.post.dto;

import com.plog.domain.post.entity.AttachmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class PostDto {
    private PostDto() {}

    public record AttachmentRequest(
            AttachmentType attachmentType,
            String fileName,
            Long fileSize,
            String fileKey,
            String fileUrl
    ) {}

    public record CreateRequest(
            @NotBlank @Size(max = 5000) String content,
            @Size(max = 10) List<@Valid AttachmentRequest> attachments
    ) {}

    public record UpdateRequest(
            @Size(min = 1, max = 5000) String content,
            @Size(max = 10) List<@Valid AttachmentRequest> attachments
    ) {}

    public record AttachmentResponse(
            Long postAttachmentId,
            AttachmentType attachmentType,
            String fileName,
            Long fileSize,
            String fileUrl
    ) {}

    public record Response(
            Long postId,
            Long projectId,
            Long projectMemberId,
            String authorNickname,
            String content,
            long likeCount,
            long commentCount,
            boolean likedByMe,
            List<AttachmentResponse> attachments,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record FeedResponse(List<Response> posts, String nextCursor, boolean hasNext) {}

    public record LikeResponse(Long postId, boolean liked, long likeCount) {}

    public record DeletedResponse(boolean deleted) {}
}
