package com.plog.domain.integration.service;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * GitHub 호출 간격을 벌리고 남은 rate limit 예산을 감시한다.
 *
 * <p>{@code NotionApiRateLimiter}와 같은 역할이지만, GitHub는 응답 헤더로 남은 예산을 알려주므로
 * 소진 직전에 수집을 멈춰 다음 시도에 온전한 예산을 남긴다.</p>
 */
@Component
@RequiredArgsConstructor
class GithubApiRateLimiter {

    private static final String REMAINING_HEADER = "x-ratelimit-remaining";
    private static final String RESET_HEADER = "x-ratelimit-reset";
    private static final long DEFAULT_RESET_SECONDS = 3_600L;

    private final IntegrationCollectionProperties properties;

    private long nextRequestAtNanos = System.nanoTime();

    synchronized void acquire() {
        long waitNanos = nextRequestAtNanos - System.nanoTime();
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("GitHub API rate-limit wait was interrupted", exception);
            }
        }
        nextRequestAtNanos = System.nanoTime() + properties.githubMinIntervalMs() * 1_000_000L;
    }

    /** 응답 헤더의 남은 예산이 임계치 아래면 수집 중단 신호를 던진다. */
    void observe(HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        String remaining = headers.getFirst(REMAINING_HEADER);
        if (remaining == null || remaining.isBlank()) {
            return;
        }
        int remainingCount;
        try {
            remainingCount = Integer.parseInt(remaining.trim());
        } catch (NumberFormatException invalidHeader) {
            return;
        }
        if (remainingCount >= properties.rateLimitMinRemaining()) {
            return;
        }
        throw new CollectionRetryableException(
                "github rate limit budget exhausted, remaining=" + remainingCount, resetAt(headers));
    }

    private Instant resetAt(HttpHeaders headers) {
        String reset = headers.getFirst(RESET_HEADER);
        if (reset == null || reset.isBlank()) {
            return Instant.now().plusSeconds(DEFAULT_RESET_SECONDS);
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(reset.trim()));
        } catch (NumberFormatException invalidHeader) {
            return Instant.now().plusSeconds(DEFAULT_RESET_SECONDS);
        }
    }
}
