package com.plog.domain.integration.service;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import com.plog.domain.integration.entity.CollectionPhase;
import jakarta.persistence.PersistenceException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/**
 * 큐에 쌓인 수집 잡을 HTTP 요청 밖에서 처리한다.
 *
 * <p>수집 1건이 GitHub API를 수백 회 호출해 수 분이 걸린다. 요청 스레드에서 처리하면 ALB
 * 타임아웃에 끊기고, 그때도 서버 스레드는 계속 돌아 rate limit만 태운다.</p>
 *
 * <p>{@code SchedulingConfig}의 주석대로 인스턴스를 늘리면 배치가 중복 실행되지만, 여기서는
 * {@code claimNext}의 비관적 락 선점과 claim token 검증이 흡수한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class IntegrationCollectionJobWorker {

    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private static final int MAX_FAILURE_SUMMARY_LENGTH = 2_000;

    private final IntegrationCollectionJobService jobService;
    private final IntegrationDataCollectionService collectionService;
    private final IntegrationCollectionProperties properties;

    @Scheduled(fixedDelayString = "${plog.integration.collection.poll-delay-ms:5000}")
    public void processDueJobs() {
        int reclaimed = jobService.reclaimStale(Instant.now());
        if (reclaimed > 0) {
            log.warn("Reclaimed stale integration collection jobs. count={}", reclaimed);
        }
        for (int index = 0; index < properties.batchSize(); index++) {
            IntegrationCollectionJobService.ClaimedJob job = jobService.claimNext(Instant.now());
            if (job == null) {
                return;
            }
            try {
                process(job);
            } catch (DataAccessException | TransactionException | PersistenceException exception) {
                // DB 장애에서 상태 저장까지 강행하지 않는다. heartbeat 만료가 재선점한다.
                log.error("Integration collection job persistence failed. jobId={}", job.jobId(), exception);
                return;
            } catch (RuntimeException exception) {
                log.error("Unexpected integration collection failure. jobId={}", job.jobId(), exception);
                jobService.fail(job, Instant.now(), "unexpected collection failure");
            }
        }
    }

    private void process(IntegrationCollectionJobService.ClaimedJob job) {
        IntegrationDataCollectionService.CollectionOutcome outcome;
        try {
            outcome = collectionService.runCollection(job.projectId(), new WorkerCollectionContext(job));
        } catch (CollectionRetryableException retryable) {
            requeue(job, retryable.nextAttemptAt(), retryable.getMessage());
            return;
        }

        Instant now = Instant.now();
        if (outcome.failures().isEmpty()) {
            jobService.succeed(job, now,
                    outcome.requestedResourceCount(), outcome.collectedResourceCount());
            return;
        }
        String summary = summarize(outcome.failures());
        if (outcome.collectedResourceCount() > 0) {
            jobService.partiallyFail(job, now,
                    outcome.requestedResourceCount(), outcome.collectedResourceCount(), summary);
            return;
        }
        jobService.fail(job, now, summary);
    }

    /** 커서를 남긴 채 다음 시도로 넘긴다. 시도 예산을 다 쓴 경우에만 실패로 확정한다. */
    private void requeue(
            IntegrationCollectionJobService.ClaimedJob job, Instant requestedAt, String summary) {
        Instant now = Instant.now();
        if (job.attemptCount() >= properties.maxAttempts()) {
            jobService.fail(job, now, summary + " (max attempts exhausted)");
            return;
        }
        Instant nextAttemptAt = requestedAt == null ? now.plus(backoff(job.attemptCount())) : requestedAt;
        Instant ceiling = now.plus(MAX_RETRY_DELAY);
        jobService.retry(job, now, nextAttemptAt.isAfter(ceiling) ? ceiling : nextAttemptAt, summary);
    }

    private Duration backoff(int attemptCount) {
        Duration delay = BASE_RETRY_DELAY.multipliedBy(1L << Math.min(attemptCount - 1, 10));
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private String summarize(List<IntegrationDataCollectionService.CollectionFailure> failures) {
        String summary = failures.stream()
                .map(failure -> failure.resourceName() + ": " + failure.reason())
                .reduce((left, right) -> left + "; " + right)
                .orElse("collection failed");
        return summary.length() <= MAX_FAILURE_SUMMARY_LENGTH
                ? summary
                : summary.substring(0, MAX_FAILURE_SUMMARY_LENGTH);
    }

    /** 진행 보고를 받아 커서와 heartbeat를 주기적으로 저장한다. */
    private final class WorkerCollectionContext implements CollectionContext {

        private final IntegrationCollectionJobService.ClaimedJob job;
        private Long currentResourceId;
        private int pendingAdvances;
        private Instant lastHeartbeatAt = Instant.now();

        private WorkerCollectionContext(IntegrationCollectionJobService.ClaimedJob job) {
            this.job = job;
        }

        @Override
        public CollectionCursor cursor() {
            return job.cursor();
        }

        @Override
        public void enterResource(Long resourceId) {
            currentResourceId = resourceId;
            pendingAdvances = 0;
            jobService.saveProgress(job, resourceId, CollectionPhase.COMMITS, null, Instant.now());
            lastHeartbeatAt = Instant.now();
        }

        @Override
        public void advance(CollectionPhase phase, int itemNumber) {
            // 매 항목마다 쓰면 DB 부하가 크고, 너무 드물게 쓰면 재개가 부정확해진다.
            if (++pendingAdvances < properties.cursorFlushInterval()) {
                return;
            }
            pendingAdvances = 0;
            jobService.saveProgress(job, currentResourceId, phase, itemNumber, Instant.now());
            lastHeartbeatAt = Instant.now();
        }

        /**
         * commit 페이지네이션처럼 항목 경계가 없는 구간에서도 생존 신호를 남긴다.
         * 간격은 processing timeout에서 유도해, timeout을 줄여도 여유가 유지되게 한다.
         */
        @Override
        public void heartbeat() {
            Instant now = Instant.now();
            if (now.isBefore(lastHeartbeatAt.plus(properties.processingTimeout().dividedBy(10)))) {
                return;
            }
            lastHeartbeatAt = now;
            jobService.heartbeat(job, now);
        }
    }
}
