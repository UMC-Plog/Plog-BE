package com.plog.domain.integration.entity;

import com.plog.domain.project.entity.Project;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로젝트 완료 시 한 번 생성되는 최종 데이터 수집 실행 이력이다. */
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "integration_collection_runs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_final_collection_run", columnNames = "project_id")
})
public class IntegrationCollectionRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integration_collection_run_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IntegrationCollectionRunStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "failure_summary", columnDefinition = "TEXT")
    private String failureSummary;

    @Column(name = "attempt_token", length = 36)
    private String attemptToken;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public String begin(Instant now) {
        if (this.status == IntegrationCollectionRunStatus.SUCCEEDED) {
            throw new IllegalStateException("completed collection run cannot be started again");
        }
        String token = UUID.randomUUID().toString();
        this.status = IntegrationCollectionRunStatus.RUNNING;
        this.attemptCount++;
        this.startedAt = now;
        this.heartbeatAt = now;
        this.finishedAt = null;
        this.failureSummary = null;
        this.attemptToken = token;
        return token;
    }

    public void heartbeat(String token, Instant now) {
        requireCurrentAttempt(token);
        this.heartbeatAt = now;
    }

    public void succeed(String token, Instant now) {
        requireCurrentAttempt(token);
        this.status = IntegrationCollectionRunStatus.SUCCEEDED;
        this.finishedAt = now;
        this.heartbeatAt = now;
    }

    public void partiallyFail(String token, Instant now, String failureSummary) {
        requireCurrentAttempt(token);
        this.status = IntegrationCollectionRunStatus.PARTIAL_FAILED;
        this.finishedAt = now;
        this.heartbeatAt = now;
        this.failureSummary = failureSummary;
    }

    public void markRetryable(String token, Instant now, String failureSummary) {
        requireCurrentAttempt(token);
        this.status = IntegrationCollectionRunStatus.RETRYABLE;
        this.finishedAt = now;
        this.heartbeatAt = now;
        this.failureSummary = failureSummary;
    }

    private void requireCurrentAttempt(String token) {
        if (this.status != IntegrationCollectionRunStatus.RUNNING
                || token == null
                || !Objects.equals(this.attemptToken, token)) {
            throw new IllegalStateException("collection run attempt is no longer active");
        }
    }
}
