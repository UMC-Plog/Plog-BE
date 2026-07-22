package com.plog.infrastructure.s3;

import com.plog.global.api.code.BaseErrorCode;
import com.plog.global.api.exception.ApiException;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 게시글·업무카드가 공유하는 첨부 검증.
 * <p>
 * 보안 검증을 도메인마다 복사하면 한쪽만 고쳐질 때 구멍이 나므로 알맹이를 한 곳에 모은다.
 * 에러 코드는 호출자가 넘긴다 — 도메인마다 기존 응답 코드가 달라서, 여기서 정하면
 * 클라이언트 계약이 바뀐다.
 */
@Component
@RequiredArgsConstructor
public class AttachmentPolicy {

    private static final int MAX_ATTACHMENTS = 10;

    private final FileStorageService fileStorageService;

    public void validateCount(int size, BaseErrorCode invalidRequestCode) {
        if (size > MAX_ATTACHMENTS) {
            throw new ApiException(invalidRequestCode);
        }
    }

    /** FILE 첨부: 필수값 확인 후 S3에 실제로 올라간 파일과 대조한다. */
    public void validateFileAttachment(AttachmentUsage usage, Long userId, String fileName,
                                       Long fileSize, String fileKey,
                                       BaseErrorCode invalidRequestCode) {
        if (fileName == null || fileSize == null || fileKey == null) {
            throw new ApiException(invalidRequestCode);
        }
        fileStorageService.verifyUploadedFile(usage, userId, fileKey, fileName, fileSize);
    }

    /** LINK 첨부: https 만 허용하고 사설망·로컬호스트를 막는다(SSRF 방지). */
    public void validateLink(String value, BaseErrorCode invalidLinkCode) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null
                    || host.equalsIgnoreCase("localhost") || isPrivateLiteral(host)) {
                throw new ApiException(invalidLinkCode);
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(invalidLinkCode, exception);
        }
    }

    private boolean isPrivateLiteral(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            String[] parts = normalized.split("\\.");
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 0 || first == 10 || first == 127
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        return normalized.contains(":") && (normalized.equals("::1")
                || normalized.equals("::")
                || normalized.startsWith("fc")
                || normalized.startsWith("fd")
                || normalized.startsWith("fe80:"));
    }
}
