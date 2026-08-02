package com.plog.domain.integration.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Notion Webhook 원문과 debounce·재시도 상태를 함께 보관하는 DB 큐다. */
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notion_webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notion_webhook_event", columnNames = "event_id")
}, indexes = {
        @Index(name = "idx_notion_webhook_due", columnList = "status,available_at"),
        @Index(name = "idx_notion_webhook_group", columnList = "workspace_id,entity_id,status,available_at"),
        @Index(name = "idx_notion_webhook_stale", columnList = "status,started_at")
})
public class NotionWebhookEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notion_webhook_event_id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "subscription_id")
    private String subscriptionId;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "notion_integration_id")
    private String notionIntegrationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "parent_id")
    private String parentId;

    @Column(name = "parent_type")
    private String parentType;

    @Column(name = "authors_json", columnDefinition = "TEXT", nullable = false)
    private String authorsJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "raw_payload", columnDefinition = "TEXT", nullable = false)
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotionWebhookEventStatus status;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_summary", columnDefinition = "TEXT")
    private String failureSummary;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public void begin(String token, Instant now) {
        if (status != NotionWebhookEventStatus.PENDING
                && status != NotionWebhookEventStatus.RETRYABLE) {
            throw new IllegalStateException("Notion webhook event is not claimable");
        }
        status = NotionWebhookEventStatus.PROCESSING;
        attemptCount++;
        startedAt = now;
        finishedAt = null;
        failureSummary = null;
        claimToken = token;
    }

    public void succeed(String token, Instant now) {
        complete(token, NotionWebhookEventStatus.SUCCEEDED, now, null);
    }

    public void partiallyFail(String token, Instant now, String failure) {
        complete(token, NotionWebhookEventStatus.PARTIAL_FAILED, now, failure);
    }

    public void ignore(String token, Instant now, String reason) {
        complete(token, NotionWebhookEventStatus.IGNORED, now, reason);
    }

    public void fail(String token, Instant now, String failure) {
        complete(token, NotionWebhookEventStatus.FAILED, now, failure);
    }

    public void requireReauthorization(String token, Instant now, String failure) {
        complete(token, NotionWebhookEventStatus.REAUTH_REQUIRED, now, failure);
    }

    public void retry(String token, Instant now, Instant nextAttemptAt, String failure) {
        requireCurrentAttempt(token);
        status = NotionWebhookEventStatus.RETRYABLE;
        availableAt = nextAttemptAt;
        finishedAt = now;
        failureSummary = failure;
        claimToken = null;
    }

    public void reclaim(Instant now, Instant nextAttemptAt) {
        if (status != NotionWebhookEventStatus.PROCESSING) {
            return;
        }
        status = NotionWebhookEventStatus.RETRYABLE;
        availableAt = nextAttemptAt;
        finishedAt = now;
        failureSummary = "stale webhook collection reclaimed";
        claimToken = null;
    }

    private void complete(
            String token,
            NotionWebhookEventStatus completedStatus,
            Instant now,
            String failure
    ) {
        requireCurrentAttempt(token);
        status = completedStatus;
        finishedAt = now;
        failureSummary = failure;
        claimToken = null;
    }

    private void requireCurrentAttempt(String token) {
        if (status != NotionWebhookEventStatus.PROCESSING
                || token == null
                || !Objects.equals(claimToken, token)) {
            throw new IllegalStateException("Notion webhook attempt is no longer active");
        }
    }
}
