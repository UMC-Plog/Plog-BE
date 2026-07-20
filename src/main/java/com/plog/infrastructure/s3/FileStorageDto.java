package com.plog.infrastructure.s3;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FileStorageDto {
    private FileStorageDto() {}

    public record PresignedUploadRequest(
            @NotBlank String fileName,
            @NotBlank String contentType,
            @NotNull @Positive Long fileSize
    ) {}

    public record PresignedUploadResponse(
            String uploadUrl,
            String fileKey,
            Map<String, List<String>> signedHeaders,
            Instant expiresAt
    ) {}
}
