package com.plog.infrastructure.s3;

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
    public record PresignedUploadRequest(
            @NotBlank String fileName,
            @NotBlank String contentType,
            @NotNull @Positive Long fileSize,
            @NotNull AttachmentUsage usage
    ) {}

    public record PresignedUploadResponse(
            String uploadUrl,
            String fileKey,
            Map<String, List<String>> signedHeaders,
            Instant expiresAt
    ) {}

    public record PresignedDownloadResponse(
            String downloadUrl,
            long expiresInSeconds
    ) {}
}
