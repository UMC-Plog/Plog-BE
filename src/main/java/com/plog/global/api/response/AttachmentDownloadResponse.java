package com.plog.global.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 첨부 다운로드 URL 발급 응답. 게시글·업무카드가 함께 쓴다.
 * ReportPdfDownloadResponse 와 같은 모양이다 — 같은 일을 하는 API 는 응답 형태도 같아야
 * 프론트가 한 번만 배운다.
 */
@Schema(description = "첨부 다운로드 URL 발급 응답")
public record AttachmentDownloadResponse(
        @Schema(description = "첨부 ID", example = "3")
        Long attachmentId,
        @Schema(description = "다운로드될 파일명", example = "요구사항_v2.docx")
        String fileName,
        @Schema(description = "S3 presigned URL. 프론트는 이 주소로 이동한다",
                example = "https://bucket.s3.ap-northeast-2.amazonaws.com/posts/...")
        String downloadUrl,
        @Schema(description = "URL 만료 시간(초)", example = "300")
        long expiresInSeconds
) {
}
