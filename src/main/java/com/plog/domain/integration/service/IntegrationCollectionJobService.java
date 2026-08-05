package com.plog.domain.integration.service;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import com.plog.domain.integration.entity.CollectionPhase;
import com.plog.domain.integration.entity.IntegrationCollectionJob;
import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
import com.plog.domain.integration.repository.IntegrationCollectionJobRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** HTTP 트랜잭션과 외부 API 작업을 분리하는 짧은 DB 큐 트랜잭션 경계다. */
@Service
@RequiredArgsConstructor
public class IntegrationCollectionJobService {

    private static final List<IntegrationCollectionJobStatus> ACTIVE_STATUSES = List.of(
            IntegrationCollectionJobStatus.PENDING,
            IntegrationCollectionJobStatus.RUNNING,
            IntegrationCollectionJobStatus.RETRYABLE
    );
    private static final List<IntegrationCollectionJobStatus> CLAIMABLE_STATUSES = List.of(
            IntegrationCollectionJobStatus.PENDING,
            IntegrationCollectionJobStatus.RETRYABLE
    );
    private static final int STALE_RECLAIM_LIMIT = 50;

    private final IntegrationCollectionJobRepository integrationCollectionJobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final IntegrationCollectionProperties properties;

    /**
     * 활성 잡이 있으면 그것을 그대로 돌려준다(멱등).
     *
     * <p>Project 행을 잠가 프로젝트 단위로 직렬화한다 — 잡 행에 FOR UPDATE를 걸면 행이 없을 때
     * 아무것도 잠기지 않아 두 요청이 나란히 삽입할 수 있다.</p>
     */
    @Transactional
    public IntegrationCollectionJob enqueue(Long projectId, Long projectMemberId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new DataRetrievalFailureException(
                        "Project disappeared while enqueueing collection: " + projectId));
        List<IntegrationCollectionJob> active = integrationCollectionJobRepository
                .findByProjectIdAndStatuses(projectId, ACTIVE_STATUSES);
        if (!active.isEmpty()) {
            return active.getFirst();
        }
        ProjectMember requestedBy = projectMemberId == null
                ? null
                : projectMemberRepository.findById(projectMemberId).orElse(null);
        return integrationCollectionJobRepository.save(IntegrationCollectionJob.builder()
                .project(project)
                .requestedByProjectMember(requestedBy)
                .status(IntegrationCollectionJobStatus.PENDING)
                .availableAt(Instant.now())
                .attemptCount(0)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedJob claimNext(Instant now) {
        List<IntegrationCollectionJob> due = integrationCollectionJobRepository.findDueForUpdate(
                CLAIMABLE_STATUSES, now, PageRequest.of(0, 1));
        if (due.isEmpty()) {
            return null;
        }
        IntegrationCollectionJob job = due.getFirst();
        String token = job.begin(now);
        return new ClaimedJob(
                job.getId(),
                job.getProject().getId(),
                token,
                job.getAttemptCount(),
                new CollectionCursor(
                        job.getCursorResourceId(), job.getCursorPhase(), job.getCursorItemNumber())
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProgress(
            ClaimedJob job, Long resourceId, CollectionPhase phase, Integer itemNumber, Instant now) {
        IntegrationCollectionJob entity = locked(job);
        entity.saveCursor(job.claimToken(), resourceId, phase, itemNumber);
        entity.heartbeat(job.claimToken(), now);
    }

    /** 커서는 두고 생존 신호만 갱신한다. 긴 페이지네이션 중 잡이 회수되는 것을 막는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void heartbeat(ClaimedJob job, Instant now) {
        locked(job).heartbeat(job.claimToken(), now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(ClaimedJob job, Instant now, int requestedCount, int collectedCount) {
        locked(job).succeed(job.claimToken(), now, requestedCount, collectedCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void partiallyFail(
            ClaimedJob job, Instant now, int requestedCount, int collectedCount, String summary) {
        locked(job).partiallyFail(job.claimToken(), now, requestedCount, collectedCount, summary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(ClaimedJob job, Instant now, String summary) {
        locked(job).fail(job.claimToken(), now, summary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(ClaimedJob job, Instant now, Instant nextAttemptAt, String summary) {
        locked(job).retry(job.claimToken(), now, nextAttemptAt, summary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reclaimStale(Instant now) {
        Instant staleBefore = now.minus(properties.processingTimeout());
        List<IntegrationCollectionJob> stale = integrationCollectionJobRepository.findStaleForUpdate(
                IntegrationCollectionJobStatus.RUNNING, staleBefore,
                PageRequest.of(0, STALE_RECLAIM_LIMIT));
        stale.forEach(job -> job.reclaim(now));
        return stale.size();
    }

    @Transactional(readOnly = true)
    public Optional<IntegrationCollectionJob> findLatest(Long projectId) {
        return integrationCollectionJobRepository
                .findLatestByProjectId(projectId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private IntegrationCollectionJob locked(ClaimedJob job) {
        return integrationCollectionJobRepository.findByIdForUpdate(job.jobId())
                .orElseThrow(() -> new DataRetrievalFailureException(
                        "Collection job disappeared while processing: " + job.jobId()));
    }

    public record ClaimedJob(
            Long jobId,
            Long projectId,
            String claimToken,
            int attemptCount,
            CollectionCursor cursor
    ) {
    }
}
