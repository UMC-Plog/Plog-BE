package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.config.NotionWebhookProperties;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationConnectionStatus;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import jakarta.persistence.PersistenceException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.client.RestClientResponseException;

/** 3분 debounce가 끝난 Notion 이벤트를 등록된 리소스 범위 안에서 순차 수집한다. */
@Component
@RequiredArgsConstructor
@Slf4j
class NotionWebhookCollectionWorker {

    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(2);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final NotionWebhookQueueService queueService;
    private final NotionWebhookProperties properties;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationResourceRepository integrationResourceRepository;
    private final NotionIntegrationResourceCollector notionCollector;
    private final IntegrationActivityStoreService activityStoreService;
    private final IntegrationResourceCollectionStateService resourceStateService;
    private final ProjectIntegrationService projectIntegrationService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${plog.integration.notion.webhook.poll-delay-ms:10000}")
    public void processDueEvents() {
        Instant now = Instant.now();
        int reclaimed = queueService.reclaimStale(now);
        if (reclaimed > 0) {
            log.warn("Reclaimed stale Notion webhook events. count={}", reclaimed);
        }
        for (int index = 0; index < properties.batchSize(); index++) {
            NotionWebhookBatch batch = queueService.claimNext(Instant.now());
            if (batch == null) {
                return;
            }
            try {
                process(batch);
            } catch (DataAccessException | TransactionException | PersistenceException exception) {
                // DB 장애에서 실패 상태 저장까지 강행하지 않는다. PROCESSING timeout이 재선점한다.
                log.error("Notion webhook persistence failed. eventIds={}", batch.eventIds(), exception);
                return;
            } catch (RuntimeException exception) {
                log.error("Unexpected Notion webhook collection failure. eventIds={}", batch.eventIds(), exception);
                queueService.fail(batch, Instant.now(), "unexpected collection failure");
            }
        }
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void deleteExpiredQueueEvents() {
        int deleted = queueService.deleteExpiredTerminalEvents(Instant.now());
        if (deleted > 0) {
            log.info("Deleted expired Notion webhook queue events. count={}", deleted);
        }
    }

    private void process(NotionWebhookBatch batch) {
        NotionWebhookBatch.Event latest = batch.latestEvent();
        List<ProjectIntegration> integrations = projectIntegrationRepository
                .findAllByLinkTypeAndExternalAccountIdAndConnectionStatus(
                        LinkType.NOTION,
                        latest.workspaceId(),
                        IntegrationConnectionStatus.ACTIVE
                );
        if (integrations.isEmpty()) {
            queueService.ignore(batch, Instant.now(), "no active Plog integration for workspace");
            return;
        }

        int matchedResources = 0;
        int collectedResources = 0;
        boolean reauthorizationRequired = false;
        ProviderResourceAccessException temporaryFailure = null;
        List<String> failures = new ArrayList<>();

        for (ProjectIntegration integration : integrations) {
            List<IntegrationResource> resources = integrationResourceRepository
                    .findAllByProjectIntegrationIdAndResourceStatusOrderByIdAsc(
                            integration.getId(), IntegrationResourceStatus.ACTIVE);
            if (resources.isEmpty()) {
                continue;
            }
            IntegrationResource resource;
            try {
                resource = notionCollector.findContainingResource(resources, latest.target(), CollectionContext.noop());
            } catch (ProviderResourceAccessException exception) {
                FailureResult result = handleProviderFailure(
                        integration, null, latest, exception, failures);
                reauthorizationRequired |= result.reauthorizationRequired();
                temporaryFailure = preferTemporary(temporaryFailure, result.temporaryFailure());
                continue;
            }
            if (resource == null) {
                continue;
            }
            matchedResources++;
            resourceStateService.markRunning(resource.getId(), Instant.now());
            activityStoreService.beginResourceCollection();
            try {
                storeWebhookActivities(resource, batch.events());
                notionCollector.collectChangedEntity(resource, latest.target(), CollectionContext.noop());
                resourceStateService.markCollected(resource.getId(), Instant.now());
                collectedResources++;
            } catch (ProviderResourceAccessException exception) {
                FailureResult result = handleProviderFailure(
                        integration, resource, latest, exception, failures);
                reauthorizationRequired |= result.reauthorizationRequired();
                temporaryFailure = preferTemporary(temporaryFailure, result.temporaryFailure());
            } catch (DataAccessException | TransactionException | PersistenceException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.error("Notion resource collection failed. resourceId={}", resource.getId(), exception);
                String failure = "collection failed: " + exception.getClass().getSimpleName();
                resourceStateService.markFailed(resource.getId(), Instant.now(), failure);
                failures.add(resource.getResourceName() + ": " + failure);
            } finally {
                activityStoreService.endResourceCollection();
            }
        }

        Instant now = Instant.now();
        if (temporaryFailure != null) {
            String failureSummary = summarize(failures);
            if (batch.attemptCount() < properties.maxAttempts()) {
                Duration delay = retryDelay(temporaryFailure, batch.attemptCount());
                queueService.retry(batch, now, now.plus(delay), failureSummary);
            } else if (collectedResources > 0) {
                queueService.partiallyFail(batch, now, failureSummary);
            } else {
                queueService.fail(batch, now, failureSummary);
            }
            return;
        }
        if (reauthorizationRequired && collectedResources == 0) {
            queueService.requireReauthorization(batch, now, summarize(failures));
            return;
        }
        if (matchedResources == 0) {
            queueService.ignore(batch, now, "event is outside registered project resources");
            return;
        }
        if (failures.isEmpty()) {
            queueService.succeed(batch, now);
            return;
        }
        String failureSummary = summarize(failures);
        if (collectedResources > 0) {
            queueService.partiallyFail(batch, now, failureSummary);
            return;
        }
        queueService.fail(batch, now, failureSummary);
    }

    private FailureResult handleProviderFailure(
            ProjectIntegration integration,
            IntegrationResource resource,
            NotionWebhookBatch.Event event,
            ProviderResourceAccessException exception,
            List<String> failures
    ) {
        int status = exception.statusCode();
        if (status == 401 || status == 403) {
            projectIntegrationService.requireReauthorization(integration.getId());
            if (resource != null) {
                resourceStateService.requireReauthorization(resource.getId(), Instant.now());
            }
            failures.add("Notion workspace reauthorization required");
            return new FailureResult(true, null);
        }
        if (status == 404) {
            if (resource != null && resource.getProviderResourceId().equals(event.entityId())) {
                resourceStateService.disable(resource.getId(), Instant.now());
                failures.add(resource.getResourceName() + ": provider resource not found");
            } else if (resource != null) {
                // 삭제된 하위 페이지·블록 이벤트 자체는 이미 보존됐으므로 루트 리소스는 유지한다.
                resourceStateService.markCollected(resource.getId(), Instant.now());
            }
            return new FailureResult(false, null);
        }
        if (status == 429 || status >= 500) {
            if (resource != null) {
                resourceStateService.markRetrying(
                        resource.getId(), Instant.now(), "provider temporarily unavailable");
            }
            failures.add((resource == null ? "Notion workspace" : resource.getResourceName())
                    + ": provider temporarily unavailable");
            return new FailureResult(false, exception);
        }
        if (resource != null) {
            resourceStateService.markFailed(
                    resource.getId(), Instant.now(), "provider request failed: HTTP " + status);
        }
        failures.add((resource == null ? "Notion workspace" : resource.getResourceName())
                + ": provider request failed: HTTP " + status);
        return new FailureResult(false, null);
    }

    private void storeWebhookActivities(
            IntegrationResource resource,
            List<NotionWebhookBatch.Event> events
    ) {
        for (NotionWebhookBatch.Event event : events) {
            JsonNode authors = readAuthors(event.authorsJson());
            if (authors.size() == 0) {
                storeWebhookActivity(resource, event, null, "unknown");
                continue;
            }
            for (JsonNode author : authors) {
                String actorId = author.path("id").asText(null);
                storeWebhookActivity(resource, event, author, actorId == null ? "unknown" : actorId);
            }
        }
    }

    private void storeWebhookActivity(
            IntegrationResource resource,
            NotionWebhookBatch.Event event,
            JsonNode author,
            String actorKey
    ) {
        activityStoreService.store(
                resource,
                IntegrationActivityType.NOTION_WEBHOOK_EVENT,
                "webhook:" + event.eventId() + ":" + actorKey,
                author == null ? null : author.path("id").asText(null),
                author == null ? null : author.path("name").asText(null),
                author == null ? null : author.path("person").path("email").asText(null),
                event.occurredAt(),
                resource.getResourceUrl(),
                event.rawPayload()
        );
    }

    private JsonNode readAuthors(String authorsJson) {
        try {
            JsonNode authors = objectMapper.readTree(authorsJson);
            return authors != null && authors.isArray() ? authors : objectMapper.createArrayNode();
        } catch (Exception exception) {
            log.warn("Invalid Notion webhook authors payload", exception);
            return objectMapper.createArrayNode();
        }
    }

    private ProviderResourceAccessException preferTemporary(
            ProviderResourceAccessException current,
            ProviderResourceAccessException candidate
    ) {
        if (candidate == null) {
            return current;
        }
        if (current == null || retryAfter(candidate) != null) {
            return candidate;
        }
        return current;
    }

    private Duration retryDelay(ProviderResourceAccessException exception, int attempt) {
        Duration retryAfter = retryAfter(exception);
        if (retryAfter != null) {
            return retryAfter.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : retryAfter;
        }
        Duration delay = BASE_RETRY_DELAY.multipliedBy(1L << Math.min(attempt - 1, 10));
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private Duration retryAfter(ProviderResourceAccessException exception) {
        if (!(exception.getCause() instanceof RestClientResponseException responseException)
                || responseException.getResponseHeaders() == null) {
            return null;
        }
        String value = responseException.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0L, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            try {
                Duration delay = Duration.between(
                        Instant.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                );
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (RuntimeException invalidHeader) {
                return null;
            }
        }
    }

    private String summarize(List<String> failures) {
        if (failures.isEmpty()) {
            return "provider temporarily unavailable";
        }
        String summary = String.join("; ", failures);
        return summary.length() <= 2_000 ? summary : summary.substring(0, 2_000);
    }

    private record FailureResult(
            boolean reauthorizationRequired,
            ProviderResourceAccessException temporaryFailure
    ) {
    }
}
