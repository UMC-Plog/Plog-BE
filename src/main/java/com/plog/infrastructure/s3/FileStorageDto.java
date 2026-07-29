package com.plog.infrastructure.s3;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FileStorageDto {
    private FileStorageDto() {}

    // usage 에 기본값을 두지 않는다. 잘못된 용도로 올라간 파일은 나중에 도메인별
    // 후처리를 망가뜨리는데, 그때는 이미 데이터가 쌓인 뒤다.
    @Schema(description = "업로드할 파일의 메타데이터. 3단계에서 첨부할 때 fileName·fileSize 를 "
            + "여기 보낸 값과 똑같이 보내야 하며, 다르면 첨부가 거부된다.")
    public record PresignedUploadRequest(
            @Schema(description = "원본 파일명(확장자 포함). 허용 확장자: "
                    + "pdf, pptx, docx, zip, fig, jpg, jpeg, png, webp, gif",
                    example = "meeting-notes.pdf")
            @NotBlank String fileName,

            @Schema(description = "파일의 MIME 타입. 확장자와 일치해야 한다.",
                    example = "application/pdf")
            @NotBlank String contentType,

            @Schema(description = "파일 크기(바이트). 이미지 10MB, 그 외 50MB 제한.",
                    example = "1024")
            @NotNull @Positive Long fileSize,

            @Schema(description = "파일이 쓰일 도메인. 3단계에서 실제 첨부하는 도메인과 일치해야 한다.",
                    example = "POST")
            @NotNull AttachmentUsage usage
    ) {}

    public record PresignedUploadResponse(
            @Schema(description = "2단계에서 PUT 할 S3 주소. signedHeaders 를 그대로 실어 보내야 한다.")
            String uploadUrl,

            @Schema(description = "파일 식별자. 게시글 **수정** 시 유지할 기존 첨부에 이 값을 보낸다. "
                    + "신규 첨부에는 fileKey 를 쓰며, 한 첨부에 둘을 같이 보내면 400.",
                    example = "42")
            Long fileId,

            @Schema(description = "S3 객체 키. 3단계에서 신규 첨부를 등록할 때 이 값을 보낸다.",
                    example = "posts/users/7/3f2b.../meeting-notes.pdf")
            String fileKey,

            @Schema(description = "PUT 요청에 반드시 포함해야 하는 서명 헤더. 하나라도 빠지면 S3 가 "
                    + "서명 불일치로 403 을 반환한다. 특히 x-amz-tagging 을 잊기 쉽다.")
            Map<String, List<String>> signedHeaders,

            @Schema(description = "uploadUrl 만료 시각(발급 후 10분). 지나면 재발급받아야 한다.")
            Instant expiresAt
    ) {}

    public record PresignedDownloadResponse(
            String downloadUrl,
            long expiresInSeconds
    ) {}
}
