package com.plog.domain.integration.entity;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수동 수집 요청을 HTTP 트랜잭션 밖에서 처리하기 위한 DB 큐다.
 *
 * <p>{@code IntegrationCollectionRun}과 혼동하면 안 된다. 그쪽은 project_id에 유니크 제약이 걸린
 * "프로젝트 완료 시 1회 최종 수집"용이고, 이 엔티티는 같은 프로젝트에서 반복 실행된다.</p>
 */
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "integration_collection_jobs", indexes = {
        @Index(name = "idx_integration_collection_job_due", columnList = "status,available_at"),
        @Index(name = "idx_integration_collection_job_project", columnList = "project_id,status")
})
public class IntegrationCollectionJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integration_collection_job_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_project_member_id")
    private ProjectMember requestedByProjectMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IntegrationCollectionJobStatus status;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "failure_summary", columnDefinition = "TEXT")
    private String failureSummary;

    @Column(name = "requested_resource_count")
    private Integer requestedResourceCount;

    @Column(name = "collected_resource_count")
    private Integer collectedResourceCount;

    @Column(name = "cursor_resource_id")
    private Long cursorResourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cursor_phase")
    private CollectionPhase cursorPhase;

    @Column(name = "cursor_item_number")
    private Integer cursorItemNumber;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public String begin(Instant now) {
        if (status != IntegrationCollectionJobStatus.PENDING
                && status != IntegrationCollectionJobStatus.RETRYABLE) {
            throw new IllegalStateException("collection job is not claimable");
        }
        String token = UUID.randomUUID().toString();
        this.status = IntegrationCollectionJobStatus.RUNNING;
        this.attemptCount++;
        this.startedAt = now;
        this.heartbeatAt = now;
        this.finishedAt = null;
        this.failureSummary = null;
        this.claimToken = token;
        return token;
    }

    public void heartbeat(String token, Instant now) {
        requireCurrentAttempt(token);
        this.heartbeatAt = now;
    }

    /** 진행 커서 저장. 커서가 움직였다는 것 자체가 살아있다는 신호라 heartbeat를 겸한다. */
    public void saveCursor(String token, Long resourceId, CollectionPhase phase, Integer itemNumber) {
        requireCurrentAttempt(token);
        this.cursorResourceId = resourceId;
        this.cursorPhase = phase;
        this.cursorItemNumber = itemNumber;
    }

    public void succeed(String token, Instant now, int requestedCount, int collectedCount) {
        requireCurrentAttempt(token);
        complete(IntegrationCollectionJobStatus.SUCCEEDED, now, null);
        this.requestedResourceCount = requestedCount;
        this.collectedResourceCount = collectedCount;
    }

    public void partiallyFail(
            String token, Instant now, int requestedCount, int collectedCount, String summary) {
        requireCurrentAttempt(token);
        complete(IntegrationCollectionJobStatus.PARTIAL_FAILED, now, summary);
        this.requestedResourceCount = requestedCount;
        this.collectedResourceCount = collectedCount;
    }

    public void fail(String token, Instant now, String summary) {
        requireCurrentAttempt(token);
        complete(IntegrationCollectionJobStatus.FAILED, now, summary);
    }

    /** 커서를 유지한 채 다음 시도로 넘긴다. 다음 attempt가 중단 지점부터 이어서 한다. */
    public void retry(String token, Instant now, Instant nextAttemptAt, String summary) {
        requireCurrentAttempt(token);
        this.status = IntegrationCollectionJobStatus.RETRYABLE;
        this.finishedAt = now;
        this.heartbeatAt = now;
        this.availableAt = nextAttemptAt;
        this.failureSummary = summary;
        this.claimToken = null;
    }

    /** 최종 수집 전환 시 이미 대기 중인 재시도 잡을 즉시 다시 처리할 수 있게 한다. */
    public void makeAvailableNow(Instant now) {
        if (this.status == IntegrationCollectionJobStatus.RETRYABLE && this.availableAt.isAfter(now)) {
            this.availableAt = now;
        }
    }

    /** heartbeat가 끊긴 잡을 토큰 없이 회수한다. 커서는 유지해 진행분을 버리지 않는다. */
    public void reclaim(Instant now) {
        this.status = IntegrationCollectionJobStatus.RETRYABLE;
        this.availableAt = now;
        this.heartbeatAt = now;
        this.claimToken = null;
        this.failureSummary = "stale collection job reclaimed";
    }

    private void complete(IntegrationCollectionJobStatus terminal, Instant now, String summary) {
        this.status = terminal;
        this.finishedAt = now;
        this.heartbeatAt = now;
        this.failureSummary = summary;
        this.claimToken = null;
        this.cursorResourceId = null;
        this.cursorPhase = null;
        this.cursorItemNumber = null;
    }

    private void requireCurrentAttempt(String token) {
        if (this.status != IntegrationCollectionJobStatus.RUNNING
                || token == null
                || !Objects.equals(this.claimToken, token)) {
            throw new IllegalStateException("collection job attempt is no longer active");
        }
    }
}
