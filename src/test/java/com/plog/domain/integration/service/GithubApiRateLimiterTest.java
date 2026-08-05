package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class GithubApiRateLimiterTest {

    @Test
    @DisplayName("남은 한도가 임계치 아래면 예산 소진 예외를 던진다")
    void throwsWhenRemainingBudgetIsBelowThreshold() {
        GithubApiRateLimiter limiter = new GithubApiRateLimiter(properties(100));
        Instant resetAt = Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "42");
        headers.add("x-ratelimit-reset", String.valueOf(resetAt.getEpochSecond()));

        assertThatThrownBy(() -> limiter.observe(headers))
                .isInstanceOf(CollectionRetryableException.class)
                .extracting(exception -> ((CollectionRetryableException) exception).nextAttemptAt())
                .isEqualTo(resetAt);
    }

    @Test
    @DisplayName("남은 한도가 충분하면 통과시킨다")
    void passesWhenBudgetIsSufficient() {
        GithubApiRateLimiter limiter = new GithubApiRateLimiter(properties(100));
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "4999");

        assertThatCode(() -> limiter.observe(headers)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rate limit 헤더가 없으면 통과시킨다")
    void passesWhenHeadersAreAbsent() {
        GithubApiRateLimiter limiter = new GithubApiRateLimiter(properties(100));

        assertThatCode(() -> limiter.observe(new HttpHeaders())).doesNotThrowAnyException();
        assertThatCode(() -> limiter.observe(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reset 헤더가 없으면 한 시간 뒤로 잡는다")
    void fallsBackToOneHourWhenResetHeaderIsMissing() {
        GithubApiRateLimiter limiter = new GithubApiRateLimiter(properties(100));
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "0");

        assertThatThrownBy(() -> limiter.observe(headers))
                .isInstanceOf(CollectionRetryableException.class)
                .extracting(exception -> ((CollectionRetryableException) exception).nextAttemptAt())
                .satisfies(resetAt -> assertThat((Instant) resetAt)
                        .isAfter(Instant.now().plusSeconds(3_000)));
    }

    @Test
    @DisplayName("숫자가 아닌 remaining 헤더는 무시하고 통과시킨다")
    void ignoresNonNumericRemainingHeader() {
        GithubApiRateLimiter limiter = new GithubApiRateLimiter(properties(100));
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "unknown");

        assertThatCode(() -> limiter.observe(headers)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("숫자가 아닌 reset 헤더는 한 시간 뒤로 대체한다")
    void fallsBackWhenResetHeaderIsNonNumeric() {
        GithubApiRateLimiter limiter = new GithubApiRateLimiter(properties(100));
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "0");
        headers.add("x-ratelimit-reset", "not-a-number");

        assertThatThrownBy(() -> limiter.observe(headers))
                .isInstanceOf(CollectionRetryableException.class)
                .extracting(exception -> ((CollectionRetryableException) exception).nextAttemptAt())
                .satisfies(resetAt -> assertThat((Instant) resetAt)
                        .isAfter(Instant.now().plusSeconds(3_000)));
    }

    private IntegrationCollectionProperties properties(int minRemaining) {
        return new IntegrationCollectionProperties(
                5_000L, 5, Duration.ofMinutes(30), 5, 25, Duration.ofHours(1), 0L, minRemaining);
    }
}
