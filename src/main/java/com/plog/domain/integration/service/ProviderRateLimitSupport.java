package com.plog.domain.integration.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;

/**
 * provider가 반환한 rate limit 응답을 권한 거부와 구분한다.
 *
 * <p>GitHub는 primary rate limit 초과에 403을 반환한다. 이를 권한 거부로 오인하면 멀쩡한 App
 * 설치를 재인증 필요 상태로 떨어뜨리게 되므로, 응답 헤더로 둘을 갈라야 한다.</p>
 */
final class ProviderRateLimitSupport {

    private static final String REMAINING_HEADER = "x-ratelimit-remaining";
    private static final String RESET_HEADER = "x-ratelimit-reset";

    private ProviderRateLimitSupport() {
    }

    static boolean isRateLimited(ProviderResourceAccessException exception) {
        HttpHeaders headers = headersOf(exception);
        if (headers == null) {
            return false;
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null && !retryAfter.isBlank()) {
            return true;
        }
        return "0".equals(headers.getFirst(REMAINING_HEADER));
    }

    /** rate limit 창이 열릴 때까지 남은 시간. 판단할 근거가 없으면 null. */
    static Duration resetDelay(ProviderResourceAccessException exception, Instant now) {
        HttpHeaders headers = headersOf(exception);
        if (headers == null) {
            return null;
        }
        String reset = headers.getFirst(RESET_HEADER);
        if (reset == null || reset.isBlank()) {
            return null;
        }
        try {
            Duration delay = Duration.between(now, Instant.ofEpochSecond(Long.parseLong(reset.trim())));
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (RuntimeException invalidHeader) {
            return null;
        }
    }

    private static HttpHeaders headersOf(ProviderResourceAccessException exception) {
        return exception.getCause() instanceof RestClientResponseException responseException
                ? responseException.getResponseHeaders()
                : null;
    }
}
