package com.plog.infrastructure.s3;

import com.plog.global.api.exception.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
    private static final Duration URL_DURATION = Duration.ofMinutes(10);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "pptx", "docx", "zip");
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "zip", Set.of("application/zip", "application/x-zip-compressed")
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${plog.s3.enabled:false}")
    private boolean enabled;

    @Value("${plog.s3.bucket:}")
    private String bucket;

    public FileStorageDto.PresignedUploadResponse createUploadUrl(FileStorageDto.PresignedUploadRequest request) {
        ensureEnabled();
        validateFile(request.fileName(), request.contentType(), request.fileSize());
        String safeName = request.fileName().trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileKey = "temporary/" + UUID.randomUUID() + "/" + safeName;
        Instant expiresAt = Instant.now().plus(URL_DURATION);
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(bucket).key(fileKey).contentType(request.contentType()).contentLength(request.fileSize())
                .tagging("state=temporary").build();
        String url = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(URL_DURATION).putObjectRequest(putObject).build())
                .url().toString();
        return new FileStorageDto.PresignedUploadResponse(url, fileKey, expiresAt);
    }

    public void verifyUploadedFile(String fileKey, String fileName, long expectedSize) {
        ensureEnabled();
        validateFile(fileName, null, expectedSize);
        if (fileKey == null || !fileKey.startsWith("temporary/")) {
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
        ensureEnabled();
        GetObjectRequest getObject = GetObjectRequest.builder().bucket(bucket).key(fileKey).build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(URL_DURATION).getObjectRequest(getObject).build())
                .url().toString();
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

    private void validateFile(String fileName, String contentType, long fileSize) {
        if (fileSize > MAX_FILE_SIZE) {
            throw new ApiException(FileStorageErrorCode.FILE_SIZE_EXCEEDED);
        }
        String extension = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE);
        }
        if (contentType != null && !ALLOWED_CONTENT_TYPES.get(extension).contains(contentType)) {
            throw new ApiException(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE);
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
