package com.plog.domain.integration.service;

import java.time.Instant;
import java.util.List;

record NotionWebhookBatch(
        List<Event> events,
        String claimToken,
        int attemptCount
) {
    Event latestEvent() {
        return events.getLast();
    }

    List<Long> eventIds() {
        return events.stream().map(Event::id).toList();
    }

    record Event(
            Long id,
            String eventId,
            String workspaceId,
            String eventType,
            String entityId,
            String entityType,
            String parentId,
            String parentType,
            String authorsJson,
            Instant occurredAt,
            String rawPayload
    ) {
        NotionWebhookTarget target() {
            return new NotionWebhookTarget(entityId, entityType, parentId, parentType);
        }
    }
}
