package com.plog.infrastructure.s3;

import com.plog.global.api.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class FileStorageService {
    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    public static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final Duration URL_DURATION = Duration.ofMinutes(10);
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "pptx", "docx", "zip", "jpg", "jpeg", "png", "webp", "gif");
    // Map.of 는 최대 10쌍이라 항목이 늘면 컴파일이 깨진다 → ofEntries 로 여유를 둔다.
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("gif", Set.of("image/gif"))
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${plog.s3.enabled:false}")
    private boolean enabled;

    @Value("${plog.s3.bucket:}")
    private String bucket;

    public FileStorageDto.PresignedUploadResponse createUploadUrl(
            Long userId,
            FileStorageDto.PresignedUploadRequest request
    ) {
        ensureEnabled();
        validateFile(request.fileName(), request.contentType(), request.fileSize());
        String safeName = request.fileName().trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileKey = ownerPrefix(request.usage(), userId) + UUID.randomUUID() + "/" + safeName;
        Instant expiresAt = Instant.now().plus(URL_DURATION);
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(bucket).key(fileKey).contentType(request.contentType()).contentLength(request.fileSize())
                .tagging("state=temporary&ownerId=" + userId).build();
        var presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(URL_DURATION).putObjectRequest(putObject).build());
        return new FileStorageDto.PresignedUploadResponse(
                presigned.url().toString(), fileKey, presigned.signedHeaders(), expiresAt);
    }

    // 용도까지 대조해 다른 도메인에 올린 키를 재사용하지 못하게 막는다.
    public void verifyUploadedFile(AttachmentUsage usage, Long userId, String fileKey,
                                   String fileName, long expectedSize) {
        ensureEnabled();
        validateFile(fileName, null, expectedSize);
        if (fileKey == null || !fileKey.startsWith(ownerPrefix(usage, userId))) {
            throw new ApiException(FileStorageErrorCode.INVALID_FILE_KEY);
        }
        try {
            var head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(fileKey).build());
            String extension = extensionOf(fileName);
            if (head.contentLength() != expectedSize
                    || !ALLOWED_CONTENT_TYPES.get(extension).contains(head.contentType())) {
                throw new ApiException(FileStorageErrorCode.INVALID_FILE_KEY);
            }
        } catch (NoSuchKeyException exception) {
            throw new ApiException(FileStorageErrorCode.INVALID_FILE_KEY, exception);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new ApiException(FileStorageErrorCode.INVALID_FILE_KEY, exception);
            }
            throw new ApiException(FileStorageErrorCode.FILE_STORAGE_ERROR, exception);
        }
    }

    public String createDownloadUrl(String fileKey) {
        return createDownloadUrl(fileKey, URL_DURATION).downloadUrl();
    }

    /** 용도에 맞는 다운로드 URL. POST·TASK 는 내려받기, CHAT 은 인라인 표시. */
    public String createDownloadUrl(AttachmentUsage usage, String fileKey, String fileName) {
        return usage.forcesDownload()
                ? createDownloadUrl(fileKey, fileName, URL_DURATION).downloadUrl()
                : createDownloadUrl(fileKey, URL_DURATION).downloadUrl();
    }

    public FileStorageDto.PresignedDownloadResponse createDownloadUrl(
            String fileKey,
            Duration duration
    ) {
        return createPresignedDownloadUrl(fileKey, null, duration);
    }

    public FileStorageDto.PresignedDownloadResponse createDownloadUrl(
            String fileKey,
            String fileName,
            Duration duration
    ) {
        String contentDisposition = "attachment; filename*=UTF-8''"
                + UriUtils.encode(fileName, StandardCharsets.UTF_8);
        return createPresignedDownloadUrl(fileKey, contentDisposition, duration);
    }

    private FileStorageDto.PresignedDownloadResponse createPresignedDownloadUrl(
            String fileKey,
            String contentDisposition,
            Duration duration
    ) {
        ensureEnabled();
        GetObjectRequest.Builder getObjectBuilder = GetObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey);
        if (contentDisposition != null) {
            getObjectBuilder.responseContentDisposition(contentDisposition);
        }
        GetObjectRequest getObject = getObjectBuilder.build();
        String downloadUrl = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(duration).getObjectRequest(getObject).build())
                .url().toString();
        return new FileStorageDto.PresignedDownloadResponse(downloadUrl, duration.getSeconds());
    }

    public void delete(String fileKey) {
        ensureEnabled();
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fileKey).build());
    }

    public void markPermanent(String fileKey) {
        ensureEnabled();
        s3Client.putObjectTagging(PutObjectTaggingRequest.builder()
                .bucket(bucket).key(fileKey)
                .tagging(Tagging.builder().tagSet(Tag.builder().key("state").value("permanent").build()).build())
                .build());
    }

    private void ensureEnabled() {
        if (!enabled || bucket.isBlank()) {
            throw new ApiException(FileStorageErrorCode.FILE_STORAGE_DISABLED);
        }
    }

    private String ownerPrefix(AttachmentUsage usage, Long userId) {
        if (usage == null || userId == null || userId <= 0) {
            throw new ApiException(FileStorageErrorCode.INVALID_FILE_KEY);
        }
        return "temporary/" + usage.keySegment() + "/users/" + userId + "/";
    }

    // 확장자를 먼저 본다 — 확장자를 알아야 적용할 용량 한도가 정해진다.
    private void validateFile(String fileName, String contentType, long fileSize) {
        String extension = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE);
        }
        if (fileSize > maxSizeFor(extension)) {
            throw new ApiException(FileStorageErrorCode.FILE_SIZE_EXCEEDED);
        }
        if (contentType != null && !ALLOWED_CONTENT_TYPES.get(extension).contains(contentType)) {
            throw new ApiException(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE);
        }
    }

    private long maxSizeFor(String extension) {
        return IMAGE_EXTENSIONS.contains(extension) ? MAX_IMAGE_SIZE : MAX_FILE_SIZE;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
