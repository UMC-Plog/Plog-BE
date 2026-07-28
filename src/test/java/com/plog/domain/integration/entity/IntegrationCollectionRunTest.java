package com.plog.domain.integration.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IntegrationCollectionRunTest {

    @Test
    void rejectsLateAttemptCallbacksAfterANewAttemptStarts() {
        IntegrationCollectionRun run = IntegrationCollectionRun.builder()
                .status(IntegrationCollectionRunStatus.PENDING)
                .attemptCount(0)
                .build();
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        String firstAttempt = run.begin(now);

        run.markRetryable(firstAttempt, now.plusSeconds(10), "temporary provider failure");
        String secondAttempt = run.begin(now.plusSeconds(20));

        assertThatThrownBy(() -> run.succeed(firstAttempt, now.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class);

        run.succeed(secondAttempt, now.plusSeconds(30));

        assertThat(run.getStatus()).isEqualTo(IntegrationCollectionRunStatus.SUCCEEDED);
        assertThat(run.getAttemptCount()).isEqualTo(2);
    }
}
