package com.plog.infrastructure.s3;

import com.plog.global.api.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
            Set.of("pdf", "pptx", "docx", "zip", "fig", "jpg", "jpeg", "png", "webp", "gif");
    // Map.of 는 최대 10쌍이라 항목이 늘면 컴파일이 깨진다 → ofEntries 로 여유를 둔다.
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed")),
            // .fig 는 IANA 등록 MIME 이 없다. 브라우저의 input[type=file] 도 OS 가 모르는
            // 확장자엔 빈 문자열을 주므로, 프론트가 octet-stream 을 명시해서 보내야 한다.
            Map.entry("fig", Set.of("application/octet-stream")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("gif", Set.of("image/gif"))
    );
    /**
     * 썸네일 대상 Content-Type. 위 두 상수에서 유도한다 — 허용 목록을 고칠 때
     * 여기를 같이 고치는 것을 잊으면 조용히 어긋난다.
     */
    private static final Set<String> IMAGE_CONTENT_TYPES = IMAGE_EXTENSIONS.stream()
            .flatMap(extension -> ALLOWED_CONTENT_TYPES.get(extension).stream())
            .collect(Collectors.toUnmodifiableSet());

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${plog.s3.enabled:false}")
    private boolean enabled;

    @Value("${plog.s3.bucket:}")
    private String bucket;

    /**
     * 썸네일 대상 판정. <b>프리픽스가 아니라 Content-Type 으로 한다</b> — 채팅 첨부는
     * 이미지 전용이 아니라 pdf·zip·docx·pptx·fig 가 섞여 있다.
     */
    public static boolean isImageContentType(String contentType) {
        return contentType != null && IMAGE_CONTENT_TYPES.contains(contentType);
    }

    /** 키는 생성 후 이동하지 않는다. 상태는 태그가 들고 있다. */
    public String buildKey(AttachmentUsage usage, Long userId, String fileName) {
        if (usage == null || userId == null || userId <= 0 || fileName == null) {
            throw new ApiException(FileStorageErrorCode.INVALID_FILE_KEY);
        }
        String safeName = fileName.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return usage.keySegment() + "/users/" + userId + "/" + UUID.randomUUID() + "/" + safeName;
    }

    /** 서버가 생성한 리포트 산출물처럼 사용자 presigned 업로드를 거치지 않는 객체를 저장한다. */
    public void putGeneratedObject(String fileKey, String contentType, byte[] bytes) {
        ensureEnabled();
        if (fileKey == null || fileKey.isBlank() || contentType == null || contentType.isBlank() || bytes == null) {
            throw new IllegalArgumentException("generated object key, content type and bytes are required");
        }
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(fileKey)
                        .contentType(contentType)
                        .contentLength((long) bytes.length)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    public FileStorageDto.PresignedUploadResponse createUploadUrl(
            Long userId,
            FileStorageDto.PresignedUploadRequest request,
            String fileKey
    ) {
        ensureEnabled();
        validateFile(request.fileName(), request.contentType(), request.fileSize());
        Instant expiresAt = Instant.now().plus(URL_DURATION);
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(bucket).key(fileKey).contentType(request.contentType())
                .contentLength(request.fileSize())
                .tagging("state=" + UploadedFileStatus.PENDING.tagValue() + "&ownerId=" + userId)
                .build();
        var presigned = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(URL_DURATION).putObjectRequest(putObject).build());
        return new FileStorageDto.PresignedUploadResponse(
                presigned.url().toString(), null, fileKey, presigned.signedHeaders(), expiresAt);
    }

    /** 업로드 실체 대조. 소유권·용도 검증은 레지스트리가 담당한다. */
    public boolean headMatches(String fileKey, long expectedSize, String expectedContentType) {
        ensureEnabled();
        try {
            var head = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(fileKey).build());
            return head.contentLength() == expectedSize
                    && expectedContentType.equals(head.contentType());
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new ApiException(FileStorageErrorCode.FILE_STORAGE_ERROR, exception);
        }
    }

    /**
     * 객체 존재 여부만 본다. 썸네일 생성 완료 판정용이라 크기·타입을 대조하지 않는다 —
     * 그 값들은 Lambda 가 정하므로 백엔드가 기대값을 갖고 있지 않다.
     */
    public boolean exists(String fileKey) {
        ensureEnabled();
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(fileKey).build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new ApiException(FileStorageErrorCode.FILE_STORAGE_ERROR, exception);
        }
    }

    public String createDownloadUrl(String fileKey) {
        return createDownloadUrl(fileKey, URL_DURATION).downloadUrl();
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

    /**
     * 프록시 전달용 S3 객체 스트림. 호출자가 반드시 닫아야 한다 —
     * 새면 S3 커넥션 풀이 조용히 고갈되고 "한참 뒤 전체 첨부 먹통"으로 나타난다.
     * <p>
     * 객체가 없으면 Optional.empty(). headMatches/applyState 가 NoSuchKey 를 false 로
     * 돌려주는 것과 같은 규약이다 — 도메인 에러코드는 호출자가 정한다.
     */
    public Optional<ResponseInputStream<GetObjectResponse>> openStream(String fileKey) {
        ensureEnabled();
        try {
            return Optional.of(s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(fileKey).build()));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw new ApiException(FileStorageErrorCode.FILE_STORAGE_ERROR, exception);
        }
    }

    /**
     * 현재 상태를 오브젝트 태그에 반영한다. 객체가 없으면 false — 정리할 대상이
     * 없다는 뜻이므로 호출자는 성공으로 처리한다.
     * <p>
     * putObjectTagging 은 태그 세트를 통째로 교체하므로 ownerId 를 매번 같이 쓴다.
     * state 만 쓰면 업로드 시 붙은 ownerId 가 승격 순간 사라진다.
     */
    public boolean applyState(String fileKey, UploadedFileStatus status, Long ownerId) {
        ensureEnabled();
        try {
            s3Client.putObjectTagging(PutObjectTaggingRequest.builder()
                    .bucket(bucket).key(fileKey)
                    .tagging(Tagging.builder().tagSet(
                            Tag.builder().key("state").value(status.tagValue()).build(),
                            Tag.builder().key("ownerId").value(String.valueOf(ownerId)).build()
                    ).build())
                    .build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new ApiException(FileStorageErrorCode.FILE_STORAGE_ERROR, exception);
        }
    }

    private void ensureEnabled() {
        if (!enabled || bucket.isBlank()) {
            throw new ApiException(FileStorageErrorCode.FILE_STORAGE_DISABLED);
        }
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
