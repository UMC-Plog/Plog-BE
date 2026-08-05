package com.plog.domain.integration.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntegrationCollectionPropertiesTest {

    @Test
    @DisplayName("정상 설정은 통과한다")
    void acceptsValidConfiguration() {
        assertThatCode(() -> properties(5_000L, 5, 25)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("poll delay가 100ms 미만이면 거부한다")
    void rejectsTooFastPolling() {
        assertThatThrownBy(() -> properties(50L, 5, 25))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cursor flush interval이 1 미만이면 거부한다")
    void rejectsNonPositiveFlushInterval() {
        assertThatThrownBy(() -> properties(5_000L, 5, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("processing timeout이 없으면 거부한다")
    void rejectsMissingProcessingTimeout() {
        assertThatThrownBy(() -> new IntegrationCollectionProperties(
                5_000L, 5, null, 5, 25, Duration.ofHours(1), 100L, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private IntegrationCollectionProperties properties(long pollDelayMs, int batchSize, int flushInterval) {
        return new IntegrationCollectionProperties(
                pollDelayMs, batchSize, Duration.ofMinutes(30), 5,
                flushInterval, Duration.ofHours(1), 100L, 100);
    }
}
