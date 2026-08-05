package com.plog.domain.integration.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntegrationCollectionJobTest {

    private static final Instant NOW = Instant.parse("2026-08-05T13:00:00Z");

    @Test
    @DisplayName("재선점 후에는 이전 attempt의 콜백을 거부한다")
    void rejectsCallbacksFromSupersededAttempt() {
        IntegrationCollectionJob job = pendingJob();
        String first = job.begin(NOW);
        job.retry(first, NOW.plusSeconds(10), NOW.plusSeconds(70), "rate limited");
        String second = job.begin(NOW.plusSeconds(70));

        assertThatThrownBy(() -> job.succeed(first, NOW.plusSeconds(80), 1, 1))
                .isInstanceOf(IllegalStateException.class);

        job.succeed(second, NOW.plusSeconds(80), 3, 3);

        assertThat(job.getStatus()).isEqualTo(IntegrationCollectionJobStatus.SUCCEEDED);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getCollectedResourceCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("retry는 다음 시도 시각을 설정하고 RETRYABLE로 되돌린다")
    void retrySchedulesNextAttempt() {
        IntegrationCollectionJob job = pendingJob();
        String token = job.begin(NOW);

        job.retry(token, NOW.plusSeconds(5), NOW.plusSeconds(3605), "rate limited");

        assertThat(job.getStatus()).isEqualTo(IntegrationCollectionJobStatus.RETRYABLE);
        assertThat(job.getAvailableAt()).isEqualTo(NOW.plusSeconds(3605));
        assertThat(job.getFailureSummary()).isEqualTo("rate limited");
    }

    @Test
    @DisplayName("retry는 커서를 보존해 다음 시도가 이어서 진행하게 한다")
    void retryKeepsCursor() {
        IntegrationCollectionJob job = pendingJob();
        String token = job.begin(NOW);
        job.saveCursor(token, 7L, CollectionPhase.ISSUES, 42);

        job.retry(token, NOW.plusSeconds(5), NOW.plusSeconds(3605), "rate limited");

        assertThat(job.getCursorResourceId()).isEqualTo(7L);
        assertThat(job.getCursorPhase()).isEqualTo(CollectionPhase.ISSUES);
        assertThat(job.getCursorItemNumber()).isEqualTo(42);
    }

    @Test
    @DisplayName("종료 상태로 끝나면 커서를 비운다")
    void clearsCursorOnTerminalState() {
        IntegrationCollectionJob job = pendingJob();
        String token = job.begin(NOW);
        job.saveCursor(token, 7L, CollectionPhase.ISSUES, 42);

        job.succeed(token, NOW.plusSeconds(10), 1, 1);

        assertThat(job.getCursorResourceId()).isNull();
        assertThat(job.getCursorPhase()).isNull();
        assertThat(job.getCursorItemNumber()).isNull();
    }

    @Test
    @DisplayName("재선점된 잡에는 이전 attempt가 커서를 덮어쓸 수 없다")
    void rejectsCursorSaveFromSupersededAttempt() {
        IntegrationCollectionJob job = pendingJob();
        String first = job.begin(NOW);
        job.retry(first, NOW.plusSeconds(10), NOW.plusSeconds(70), "rate limited");
        job.begin(NOW.plusSeconds(70));

        assertThatThrownBy(() -> job.saveCursor(first, 7L, CollectionPhase.ISSUES, 42))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reclaim은 토큰 없이 RETRYABLE로 회수한다")
    void reclaimRecoversStaleJob() {
        IntegrationCollectionJob job = pendingJob();
        job.begin(NOW);

        job.reclaim(NOW.plusSeconds(1_800));

        assertThat(job.getStatus()).isEqualTo(IntegrationCollectionJobStatus.RETRYABLE);
        assertThat(job.getAvailableAt()).isEqualTo(NOW.plusSeconds(1_800));
    }

    @Test
    @DisplayName("RUNNING이 아니면 heartbeat를 거부한다")
    void rejectsHeartbeatWhenNotRunning() {
        IntegrationCollectionJob job = pendingJob();

        assertThatThrownBy(() -> job.heartbeat("any-token", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("종료 상태인 잡은 다시 선점할 수 없다")
    void rejectsClaimOnTerminalJob() {
        IntegrationCollectionJob job = pendingJob();
        String token = job.begin(NOW);
        job.succeed(token, NOW.plusSeconds(10), 1, 1);

        assertThatThrownBy(() -> job.begin(NOW.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class);
    }

    private IntegrationCollectionJob pendingJob() {
        return IntegrationCollectionJob.builder()
                .status(IntegrationCollectionJobStatus.PENDING)
                .availableAt(NOW)
                .attemptCount(0)
                .build();
    }
}
