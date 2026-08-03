package com.plog.domain.integration.service;

import com.plog.domain.integration.config.NotionWebhookProperties;
import com.plog.domain.integration.entity.NotionWebhookEvent;
import com.plog.domain.integration.entity.NotionWebhookEventStatus;
import com.plog.domain.integration.repository.NotionWebhookEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Webhook HTTP 트랜잭션과 외부 API 작업을 분리하는 짧은 DB 큐 트랜잭션 경계다. */
@Service
@RequiredArgsConstructor
class NotionWebhookQueueService {

    private static final List<NotionWebhookEventStatus> CLAIMABLE_STATUSES = List.of(
            NotionWebhookEventStatus.PENDING,
            NotionWebhookEventStatus.RETRYABLE
    );
    private static final List<NotionWebhookEventStatus> TERMINAL_STATUSES = List.of(
            NotionWebhookEventStatus.SUCCEEDED,
            NotionWebhookEventStatus.PARTIAL_FAILED,
            NotionWebhookEventStatus.IGNORED,
            NotionWebhookEventStatus.FAILED,
            NotionWebhookEventStatus.REAUTH_REQUIRED
    );

    private final NotionWebhookEventRepository notionWebhookEventRepository;
    private final NotionWebhookProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotionWebhookBatch claimNext(Instant now) {
        List<NotionWebhookEvent> due = notionWebhookEventRepository.findDueForUpdate(
                CLAIMABLE_STATUSES, now, PageRequest.of(0, 1));
        if (due.isEmpty()) {
            return null;
        }
        NotionWebhookEvent seed = due.getFirst();
        List<NotionWebhookEvent> group = notionWebhookEventRepository.findGroupForUpdate(
                seed.getWorkspaceId(), seed.getEntityId(), CLAIMABLE_STATUSES, now);
        if (group.isEmpty()) {
            return null;
        }
        String claimToken = UUID.randomUUID().toString();
        group.forEach(event -> event.begin(claimToken, now));
        int attemptCount = group.stream().mapToInt(NotionWebhookEvent::getAttemptCount).max().orElse(1);
        return new NotionWebhookBatch(
                group.stream().map(this::snapshot).toList(), claimToken, attemptCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(NotionWebhookBatch batch, Instant now) {
        locked(batch).forEach(event -> event.succeed(batch.claimToken(), now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void partiallyFail(NotionWebhookBatch batch, Instant now, String failure) {
        locked(batch).forEach(event -> event.partiallyFail(batch.claimToken(), now, failure));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ignore(NotionWebhookBatch batch, Instant now, String reason) {
        locked(batch).forEach(event -> event.ignore(batch.claimToken(), now, reason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(NotionWebhookBatch batch, Instant now, String failure) {
        locked(batch).forEach(event -> event.fail(batch.claimToken(), now, failure));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireReauthorization(NotionWebhookBatch batch, Instant now, String failure) {
        locked(batch).forEach(event -> event.requireReauthorization(batch.claimToken(), now, failure));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(NotionWebhookBatch batch, Instant now, Instant nextAttemptAt, String failure) {
        locked(batch).forEach(event -> event.retry(batch.claimToken(), now, nextAttemptAt, failure));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reclaimStale(Instant now) {
        Instant staleBefore = now.minus(properties.processingTimeout());
        List<NotionWebhookEvent> stale = notionWebhookEventRepository.findStaleForUpdate(
                NotionWebhookEventStatus.PROCESSING, staleBefore, PageRequest.of(0, 100));
        stale.forEach(event -> {
            if (event.getAttemptCount() >= properties.maxAttempts()) {
                event.fail(event.getClaimToken(), now, "stale webhook collection exhausted");
                return;
            }
            event.reclaim(now, now.plusSeconds(1));
        });
        return stale.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpiredTerminalEvents(Instant now) {
        return notionWebhookEventRepository.deleteTerminalEventsBefore(
                TERMINAL_STATUSES, now.minus(Duration.ofDays(7)));
    }

    private List<NotionWebhookEvent> locked(NotionWebhookBatch batch) {
        List<NotionWebhookEvent> events = notionWebhookEventRepository.findAllByIdForUpdate(batch.eventIds());
        if (events.size() != batch.events().size()) {
            throw new IllegalStateException("Notion webhook batch changed while processing");
        }
        return events;
    }

    private NotionWebhookBatch.Event snapshot(NotionWebhookEvent event) {
        return new NotionWebhookBatch.Event(
                event.getId(), event.getEventId(), event.getWorkspaceId(), event.getEventType(),
                event.getEntityId(), event.getEntityType(), event.getParentId(), event.getParentType(),
                event.getAuthorsJson(), event.getOccurredAt(), event.getRawPayload()
        );
    }
}
