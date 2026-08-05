package com.plog.domain.integration.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class IntegrationCollectionJobWorkerTest {

    private static final Instant RESET_AT = Instant.parse("2026-08-05T14:00:00Z");

    private final IntegrationCollectionJobService jobService =
            mock(IntegrationCollectionJobService.class);
    private final IntegrationDataCollectionService collectionService =
            mock(IntegrationDataCollectionService.class);

    @Test
    @DisplayName("전 리소스가 성공하면 잡을 SUCCEEDED로 끝낸다")
    void marksJobSucceededWhenAllResourcesCollected() {
        IntegrationCollectionJobService.ClaimedJob job = claim(1);
        givenSingleClaim(job);
        given(collectionService.runCollection(eq(7L), any()))
                .willReturn(new IntegrationDataCollectionService.CollectionOutcome(3, 3, List.of()));

        worker(5).processDueJobs();

        then(jobService).should().succeed(eq(job), any(), eq(3), eq(3));
        then(jobService).should(never()).partiallyFail(any(), any(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("일부 실패면 잡을 PARTIAL_FAILED로 끝낸다")
    void marksJobPartiallyFailedWhenSomeResourcesFail() {
        IntegrationCollectionJobService.ClaimedJob job = claim(1);
        givenSingleClaim(job);
        given(collectionService.runCollection(eq(7L), any()))
                .willReturn(new IntegrationDataCollectionService.CollectionOutcome(3, 2, List.of(
                        new IntegrationDataCollectionService.CollectionFailure(
                                9L, "Plog-FE", "provider resource not found"))));

        worker(5).processDueJobs();

        then(jobService).should().partiallyFail(eq(job), any(), eq(3), eq(2),
                eq("Plog-FE: provider resource not found"));
    }

    @Test
    @DisplayName("전 리소스가 실패하면 잡을 FAILED로 끝낸다")
    void marksJobFailedWhenNoResourceCollected() {
        IntegrationCollectionJobService.ClaimedJob job = claim(1);
        givenSingleClaim(job);
        given(collectionService.runCollection(eq(7L), any()))
                .willReturn(new IntegrationDataCollectionService.CollectionOutcome(1, 0, List.of(
                        new IntegrationDataCollectionService.CollectionFailure(
                                9L, "Plog-FE", "provider resource access denied"))));

        worker(5).processDueJobs();

        then(jobService).should().fail(eq(job), any(),
                eq("Plog-FE: provider resource access denied"));
    }

    @Test
    @DisplayName("rate limit 예산 소진이면 reset 시각으로 재큐한다")
    void requeuesJobWhenRateLimitBudgetExhausted() {
        IntegrationCollectionJobService.ClaimedJob job = claim(1);
        givenSingleClaim(job);
        willThrow(new CollectionRetryableException("rate limited", RESET_AT))
                .given(collectionService).runCollection(eq(7L), any());

        worker(5).processDueJobs();

        then(jobService).should().retry(eq(job), any(), eq(RESET_AT), anyString());
        then(jobService).should(never()).fail(any(), any(), anyString());
    }

    @Test
    @DisplayName("최대 시도를 넘기면 재큐하지 않고 FAILED로 끝낸다")
    void failsJobAfterMaxAttempts() {
        IntegrationCollectionJobService.ClaimedJob job = claim(5);
        givenSingleClaim(job);
        willThrow(new CollectionRetryableException("rate limited", RESET_AT))
                .given(collectionService).runCollection(eq(7L), any());

        worker(5).processDueJobs();

        then(jobService).should().fail(eq(job), any(), anyString());
        then(jobService).should(never()).retry(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("DB 예외에서는 상태 저장을 강행하지 않는다")
    void doesNotForceStatusWriteOnPersistenceFailure() {
        IntegrationCollectionJobService.ClaimedJob job = claim(1);
        givenSingleClaim(job);
        willThrow(new DataIntegrityViolationException("db down"))
                .given(collectionService).runCollection(eq(7L), any());

        worker(5).processDueJobs();

        then(jobService).should(never()).fail(any(), any(), anyString());
        then(jobService).should(never()).retry(any(), any(), any(), anyString());
        then(jobService).should(never()).succeed(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("큐가 비어 있으면 수집을 시도하지 않는다")
    void doesNothingWhenQueueIsEmpty() {
        given(jobService.claimNext(any())).willReturn(null);

        worker(5).processDueJobs();

        then(collectionService).should(never()).runCollection(any(), any());
    }

    @Test
    @DisplayName("한 번에 batch-size 개까지만 처리한다")
    void processesAtMostBatchSizeJobsPerTick() {
        given(jobService.claimNext(any())).willReturn(claim(1));
        given(collectionService.runCollection(eq(7L), any()))
                .willReturn(new IntegrationDataCollectionService.CollectionOutcome(1, 1, List.of()));

        worker(5).processDueJobs();

        then(collectionService).should(times(5)).runCollection(eq(7L), any());
    }

    @Test
    @DisplayName("매 주기마다 좀비 잡 회수를 먼저 시도한다")
    void reclaimsStaleJobsBeforeClaiming() {
        given(jobService.claimNext(any())).willReturn(null);

        worker(5).processDueJobs();

        then(jobService).should().reclaimStale(any());
    }

    private void givenSingleClaim(IntegrationCollectionJobService.ClaimedJob job) {
        given(jobService.claimNext(any()))
                .willReturn(job, (IntegrationCollectionJobService.ClaimedJob) null);
    }

    private IntegrationCollectionJobService.ClaimedJob claim(int attemptCount) {
        return new IntegrationCollectionJobService.ClaimedJob(
                42L, 7L, "token", attemptCount, CollectionCursor.start());
    }

    private IntegrationCollectionJobWorker worker(int maxAttempts) {
        return new IntegrationCollectionJobWorker(jobService, collectionService,
                new IntegrationCollectionProperties(
                        5_000L, 5, Duration.ofMinutes(30), maxAttempts, 25,
                        Duration.ofHours(1), 0L, 100));
    }
}
