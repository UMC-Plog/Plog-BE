package com.plog.domain.integration.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotionWebhookEventTest {

    @Test
    void rejectsCompletionFromAnOlderClaimAttempt() {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        NotionWebhookEvent event = event(NotionWebhookEventStatus.PENDING);

        event.begin("first", now);
        event.retry("first", now.plusSeconds(1), now.plusSeconds(2), "temporary");
        event.begin("second", now.plusSeconds(2));

        assertThatThrownBy(() -> event.succeed("first", now.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        event.succeed("second", now.plusSeconds(3));
        assertThat(event.getStatus()).isEqualTo(NotionWebhookEventStatus.SUCCEEDED);
    }

    @Test
    void staleProcessingEventCanBeReclaimedForAnotherAttempt() {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        NotionWebhookEvent event = event(NotionWebhookEventStatus.PENDING);
        event.begin("first", now);

        event.reclaim(now.plusSeconds(60), now.plusSeconds(61));

        assertThat(event.getStatus()).isEqualTo(NotionWebhookEventStatus.RETRYABLE);
        assertThat(event.getClaimToken()).isNull();
        assertThat(event.getAvailableAt()).isEqualTo(now.plusSeconds(61));
    }

    private NotionWebhookEvent event(NotionWebhookEventStatus status) {
        return NotionWebhookEvent.builder()
                .eventId("event-1")
                .workspaceId("workspace-1")
                .eventType("page.content_updated")
                .entityId("page-1")
                .entityType("page")
                .authorsJson("[]")
                .occurredAt(Instant.parse("2026-08-02T09:59:00Z"))
                .rawPayload("{}")
                .status(status)
                .availableAt(Instant.parse("2026-08-02T10:00:00Z"))
                .build();
    }
}
