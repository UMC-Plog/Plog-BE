package com.plog.domain.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.config.NotionWebhookProperties;
import com.plog.domain.integration.entity.NotionWebhookEventStatus;
import com.plog.domain.integration.repository.NotionWebhookEventRepository;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 Webhook 요청을 검증하고 provider 호출 없이 DB 큐에 멱등 적재한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotionWebhookEventIngestionService {

    private static final int MAX_PAYLOAD_LENGTH = 1_048_576;
    private static final String DUMMY_VERIFICATION_TOKEN = "dummy_token";
    private static final List<NotionWebhookEventStatus> DEBOUNCE_STATUSES = List.of(
            NotionWebhookEventStatus.PENDING,
            NotionWebhookEventStatus.RETRYABLE
    );

    private final ObjectMapper objectMapper;
    private final NotionWebhookProperties properties;
    private final NotionWebhookSignatureVerifier signatureVerifier;
    private final NotionWebhookEventRepository notionWebhookEventRepository;
    private final AtomicReference<String> lastLoggedVerificationToken = new AtomicReference<>();

    @Transactional
    public IngestionResult ingest(String rawBody, String signature) {
        JsonNode payload = parse(rawBody);
        String verificationToken = text(payload, "verification_token");
        if (verificationToken != null) {
            logVerificationTokenIfNeeded(verificationToken);
            return IngestionResult.VERIFICATION_ACCEPTED;
        }
        if (!signatureVerifier.verify(properties.verificationToken(), rawBody, signature)) {
            return IngestionResult.INVALID_SIGNATURE;
        }

        EventFields event = fields(payload);
        Instant availableAt = Instant.now().plus(properties.debounce());
        int inserted = notionWebhookEventRepository.insertIfAbsent(
                event.eventId(), event.subscriptionId(), event.workspaceId(), event.notionIntegrationId(),
                event.eventType(), event.entityId(), event.entityType(), event.parentId(), event.parentType(),
                event.authorsJson(), event.occurredAt(), rawBody, availableAt
        );
        if (inserted == 0) {
            return IngestionResult.DUPLICATE_IGNORED;
        }
        notionWebhookEventRepository.postponePendingGroup(
                event.workspaceId(), event.entityId(), DEBOUNCE_STATUSES, availableAt);
        return IngestionResult.EVENT_ACCEPTED;
    }

    private JsonNode parse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()
                || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Invalid Notion webhook payload size");
        }
        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("Notion webhook payload must be a JSON object");
            }
            return payload;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid Notion webhook JSON", exception);
        }
    }

    private EventFields fields(JsonNode payload) {
        JsonNode entity = payload.path("entity");
        JsonNode parent = payload.path("data").path("parent");
        String eventId = required(payload, "id");
        String workspaceId = required(payload, "workspace_id");
        String eventType = required(payload, "type");
        String entityId = required(entity, "id");
        String entityType = required(entity, "type");
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(required(payload, "timestamp"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Notion webhook timestamp", exception);
        }
        return new EventFields(
                eventId,
                text(payload, "subscription_id"),
                workspaceId,
                text(payload, "integration_id"),
                eventType,
                entityId,
                entityType,
                parentId(parent),
                parentType(parent),
                payload.path("authors").isArray() ? payload.path("authors").toString() : "[]",
                occurredAt
        );
    }

    private String parentId(JsonNode parent) {
        String direct = text(parent, "id");
        if (direct != null) {
            return direct;
        }
        for (String field : List.of("page_id", "block_id", "data_source_id", "database_id")) {
            String value = text(parent, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String parentType(JsonNode parent) {
        String type = text(parent, "type");
        return type == null ? null : type.replace("_id", "");
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Missing Notion webhook field: " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }



    private void logVerificationTokenIfNeeded(String verificationToken) {
        if (isRealVerificationTokenConfigured()) {
            return;
        }

        String previous = lastLoggedVerificationToken.getAndSet(verificationToken);
        if (!verificationToken.equals(previous)) {
            log.warn("Notion webhook verification token received. "
                            + "Set NOTION_WEBHOOK_VERIFICATION_TOKEN and redeploy. tokenFingerprint={}",
                    verificationToken);
        }
    }

    private boolean isRealVerificationTokenConfigured() {
        return properties.verificationToken() != null
                && !properties.verificationToken().isBlank()
                && !DUMMY_VERIFICATION_TOKEN.equals(properties.verificationToken());
    }
    private String maskToken(String token) {
        if (token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    public enum IngestionResult {
        VERIFICATION_ACCEPTED,
        EVENT_ACCEPTED,
        DUPLICATE_IGNORED,
        INVALID_SIGNATURE
    }

    private record EventFields(
            String eventId,
            String subscriptionId,
            String workspaceId,
            String notionIntegrationId,
            String eventType,
            String entityId,
            String entityType,
            String parentId,
            String parentType,
            String authorsJson,
            Instant occurredAt
    ) {
    }
}
