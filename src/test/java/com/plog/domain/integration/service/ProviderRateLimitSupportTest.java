package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;

class ProviderRateLimitSupportTest {

    private static final Instant NOW = Instant.parse("2026-08-05T13:00:00Z");

    @Test
    @DisplayName("x-ratelimit-remaining이 0이면 rate limit으로 판정한다")
    void detectsPrimaryRateLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "0");

        assertThat(ProviderRateLimitSupport.isRateLimited(exception(403, headers))).isTrue();
    }

    @Test
    @DisplayName("retry-after 헤더가 있으면 rate limit으로 판정한다")
    void detectsSecondaryRateLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "60");

        assertThat(ProviderRateLimitSupport.isRateLimited(exception(403, headers))).isTrue();
    }

    @Test
    @DisplayName("rate limit 헤더가 없는 403은 진짜 권한 거부로 판정한다")
    void treatsPlainForbiddenAsAccessDenied() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "4999");

        assertThat(ProviderRateLimitSupport.isRateLimited(exception(403, headers))).isFalse();
    }

    @Test
    @DisplayName("응답 없이 발생한 예외는 rate limit이 아니다")
    void treatsMissingResponseAsNotRateLimited() {
        ProviderResourceAccessException noResponse =
                new ProviderResourceAccessException(503, new RuntimeException("timeout"));

        assertThat(ProviderRateLimitSupport.isRateLimited(noResponse)).isFalse();
        assertThat(ProviderRateLimitSupport.resetDelay(noResponse, NOW)).isNull();
    }

    @Test
    @DisplayName("x-ratelimit-reset으로 재개까지 남은 시간을 계산한다")
    void calculatesResetDelay() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "0");
        headers.add("x-ratelimit-reset", String.valueOf(NOW.plusSeconds(900).getEpochSecond()));

        assertThat(ProviderRateLimitSupport.resetDelay(exception(403, headers), NOW))
                .isEqualTo(Duration.ofSeconds(900));
    }

    @Test
    @DisplayName("이미 지난 reset 시각은 0으로 처리한다")
    void clampsPastResetToZero() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "0");
        headers.add("x-ratelimit-reset", String.valueOf(NOW.minusSeconds(30).getEpochSecond()));

        assertThat(ProviderRateLimitSupport.resetDelay(exception(403, headers), NOW))
                .isEqualTo(Duration.ZERO);
    }

    private ProviderResourceAccessException exception(int status, HttpHeaders headers) {
        return new ProviderResourceAccessException(status, new RestClientResponseException(
                "rate limited", status, "Forbidden", headers,
                "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }
}
