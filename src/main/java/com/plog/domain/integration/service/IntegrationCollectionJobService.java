package com.plog.domain.integration.service;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import com.plog.domain.integration.entity.CollectionPhase;
import com.plog.domain.integration.entity.IntegrationCollectionJob;
import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
import com.plog.domain.integration.event.ExternalCollectionFinishedEvent;
import com.plog.domain.integration.event.ExternalCollectionStartedEvent;
import com.plog.domain.integration.repository.IntegrationCollectionJobRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectCollectionStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

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
            IntegrationCollectionJob activeJob = active.getFirst();
            if (isFinalCollectionExpected(activeJob)) {
                activeJob.makeAvailableNow(Instant.now());
            }
            return activeJob;
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
        boolean finalCollection = isFinalCollectionExpected(job);
        String token = job.begin(now);
        if (finalCollection) {
            eventPublisher.publishEvent(new ExternalCollectionStartedEvent(job.getProject().getId()));
        }
        return new ClaimedJob(
                job.getId(),
                job.getProject().getId(),
                token,
                job.getAttemptCount(),
                finalCollection,
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
        IntegrationCollectionJob entity = locked(job);
        entity.succeed(job.claimToken(), now, requestedCount, collectedCount);
        if (isFinalCollectionExpected(entity)) {
            eventPublisher.publishEvent(new ExternalCollectionFinishedEvent(
                    entity.getProject().getId(), entity.getId(), IntegrationCollectionJobStatus.SUCCEEDED));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void partiallyFail(
            ClaimedJob job, Instant now, int requestedCount, int collectedCount, String summary) {
        IntegrationCollectionJob entity = locked(job);
        entity.partiallyFail(job.claimToken(), now, requestedCount, collectedCount, summary);
        if (isFinalCollectionExpected(entity)) {
            eventPublisher.publishEvent(new ExternalCollectionFinishedEvent(
                    entity.getProject().getId(), entity.getId(), IntegrationCollectionJobStatus.PARTIAL_FAILED));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(ClaimedJob job, Instant now, String summary) {
        IntegrationCollectionJob entity = locked(job);
        entity.fail(job.claimToken(), now, summary);
        if (isFinalCollectionExpected(entity)) {
            eventPublisher.publishEvent(new ExternalCollectionFinishedEvent(
                    entity.getProject().getId(), entity.getId(), IntegrationCollectionJobStatus.FAILED));
        }
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

    /** 수동으로 시작된 실행 중 잡이 프로젝트 마감 후 최종 수집으로 승격됐는지 다시 확인한다. */
    @Transactional(readOnly = true)
    public boolean isFinalCollectionExpected(ClaimedJob job) {
        if (job.finalCollection()) {
            return true;
        }
        return projectRepository.findById(job.projectId())
                .map(this::isFinalCollectionExpected)
                .orElse(false);
    }

    private IntegrationCollectionJob locked(ClaimedJob job) {
        return integrationCollectionJobRepository.findByIdForUpdate(job.jobId())
                .orElseThrow(() -> new DataRetrievalFailureException(
                        "Collection job disappeared while processing: " + job.jobId()));
    }

    private boolean isFinalCollectionExpected(IntegrationCollectionJob job) {
        return job.getRequestedByProjectMember() == null
                || isFinalCollectionExpected(job.getProject());
    }

    private boolean isFinalCollectionExpected(Project project) {
        ProjectCollectionStatus status = project.getExternalCollectionStatus();
        return status == ProjectCollectionStatus.PENDING
                || status == ProjectCollectionStatus.RUNNING
                || status == ProjectCollectionStatus.PARTIAL_FAILED
                || status == ProjectCollectionStatus.FAILED;
    }

    public record ClaimedJob(
            Long jobId,
            Long projectId,
            String claimToken,
            int attemptCount,
            boolean finalCollection,
            CollectionCursor cursor
    ) {
    }
}
