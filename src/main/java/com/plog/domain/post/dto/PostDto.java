package com.plog.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.plog.domain.post.entity.AttachmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class PostDto {
    private PostDto() {}

    public record AttachmentRequest(
            AttachmentType attachmentType,
            String fileName,
            Long fileSize,
            @Schema(description = "신규 첨부의 S3 키. 기존 첨부를 유지할 때는 보내지 않는다.")
            String fileKey,
            @Schema(description = "기존 첨부를 유지할 때 보내는 uploaded_file ID. "
                    + "fileKey 와 동시에 보낼 수 없다.")
            Long fileId,
            @Schema(description = "LINK 첨부의 외부 URL. FILE 첨부에는 쓰지 않는다. "
                    + "조회 응답의 linkUrl 과 같은 값이다.")
            String linkUrl
    ) {}

    public record CreateRequest(
            @NotBlank @Size(max = 5000) String content,
            boolean isNotice,
            @Size(max = 10) List<@Valid AttachmentRequest> attachments
    ) {}

    public record UpdateRequest(
            @Size(min = 1, max = 5000) String content,
            @Size(max = 10) List<@Valid AttachmentRequest> attachments
    ) {}

    public record NoticeRequest(@NotNull Boolean isNotice) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttachmentResponse(
            Long postAttachmentId,
            AttachmentType attachmentType,
            @Schema(description = "FILE 첨부의 uploaded_file ID. 수정 요청에 그대로 되돌려 보낸다.")
            Long fileId,
            String fileName,
            Long fileSize,
            @Schema(description = "LINK 첨부의 외부 링크. FILE 이면 null")
            String linkUrl,
            @Schema(description = "FILE 첨부의 다운로드 URL 발급 API 주소. 클릭 시 이 주소를 "
                    + "호출해 presigned 를 받는다. LINK 면 null. "
                    + "이 주소를 <a href> 에 걸면 JSON 이 보인다")
            String downloadUrlApi
    ) {}

    public record PostResponse(
            Long postId,
            Long projectId,
            Long projectMemberId,
            String authorNickname,
            String content,
            boolean isNotice,
            long likeCount,
            long commentCount,
            boolean likedByMe,
            List<AttachmentResponse> attachments,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateResponse(
            Long postId,
            Long projectId,
            Long projectMemberId,
            String content,
            boolean isNotice,
            long likeCount,
            long commentCount,
            boolean likedByMe,
            List<AttachmentResponse> attachments,
            Instant createdAt
    ) {}

    public record UpdateResponse(
            Long postId,
            Long projectId,
            Long projectMemberId,
            String content,
            boolean isNotice,
            long likeCount,
            long commentCount,
            boolean likedByMe,
            List<AttachmentResponse> attachments,
            Instant updatedAt
    ) {}

    public record FeedResponse(
            PostResponse notice,
            List<PostResponse> posts,
            String nextCursor,
            boolean hasNext
    ) {}

    public record NoticeResponse(Long postId, Long projectId, boolean isNotice, Instant updatedAt) {}

    public record LikeResponse(Long postId, boolean liked, long likeCount) {}

    public record DeletedResponse(boolean deleted) {}
}
