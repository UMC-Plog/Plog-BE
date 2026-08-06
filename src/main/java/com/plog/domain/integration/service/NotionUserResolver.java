package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** Notion partial user를 수집 범위 안에서 한 번만 조회해 표시 정보를 보강한다. */
@Slf4j
@Component
class NotionUserResolver {

    private static final String API_BASE_URL = "https://api.notion.com";
    private static final String NOTION_VERSION = "2026-03-11";

    private final IntegrationActivityStoreService activityStoreService;
    private final NotionApiRateLimiter rateLimiter;
    private final RestClient restClient;

    @Autowired
    NotionUserResolver(
            IntegrationActivityStoreService activityStoreService,
            NotionApiRateLimiter rateLimiter
    ) {
        this(activityStoreService, rateLimiter, ProviderRestClientFactory.create());
    }

    NotionUserResolver(
            IntegrationActivityStoreService activityStoreService,
            NotionApiRateLimiter rateLimiter,
            RestClient restClient
    ) {
        this.activityStoreService = activityStoreService;
        this.rateLimiter = rateLimiter;
        this.restClient = restClient;
    }

    Session begin(Long projectIntegrationId, String accessToken, CollectionContext context) {
        return new Session(projectIntegrationId, accessToken, context);
    }

    Actor resolve(Session session, JsonNode partialUser) {
        Actor partial = actorFrom(partialUser);
        if (partial.providerId() == null) {
            return partial;
        }

        Actor known = merge(session.knownActors.get(partial.providerId()), partial);
        if (needsLookup(known) && session.lookupAttempts.add(partial.providerId())) {
            known = merge(known, retrieve(session, partial.providerId()));
        }
        session.knownActors.put(partial.providerId(), known);
        backfillWhenImproved(session, known);
        return known;
    }

    private Actor retrieve(Session session, String userId) {
        URI uri = UriComponentsBuilder.fromUriString(API_BASE_URL)
                .pathSegment("v1", "users", userId)
                .build()
                .encode()
                .toUri();
        try {
            session.context.heartbeat();
            rateLimiter.acquire();
            JsonNode user = restClient.get()
                    .uri(uri)
                    .header("Notion-Version", NOTION_VERSION)
                    .headers(headers -> headers.setBearerAuth(session.accessToken))
                    .retrieve()
                    .body(JsonNode.class);
            return actorFrom(user);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 403) {
                log.info("Notion user information capability is unavailable. userId={}", userId);
            } else {
                log.warn("Notion user lookup failed. userId={}, status={}, body={}",
                        userId, status,
                        ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            }
            return Actor.empty(userId);
        } catch (RestClientException exception) {
            log.warn("Notion user lookup failed without a response. userId={}", userId, exception);
            return Actor.empty(userId);
        }
    }

    private void backfillWhenImproved(Session session, Actor actor) {
        Actor previous = session.backfilledActors.get(actor.providerId());
        if (!addsDisplayInformation(previous, actor)) {
            return;
        }
        activityStoreService.backfillActorDisplayInfo(
                session.projectIntegrationId,
                actor.providerId(),
                actor.login(),
                actor.email()
        );
        session.backfilledActors.put(actor.providerId(), merge(previous, actor));
    }

    private boolean addsDisplayInformation(Actor previous, Actor current) {
        if (current == null) {
            return false;
        }
        return (value(previous == null ? null : previous.login()) == null && value(current.login()) != null)
                || (value(previous == null ? null : previous.email()) == null && value(current.email()) != null);
    }

    private boolean needsLookup(Actor actor) {
        return value(actor.login()) == null || value(actor.email()) == null;
    }

    private Actor merge(Actor preferred, Actor fallback) {
        if (preferred == null) {
            return fallback;
        }
        if (fallback == null) {
            return preferred;
        }
        return new Actor(
                firstNonblank(preferred.providerId(), fallback.providerId()),
                firstNonblank(preferred.login(), fallback.login()),
                firstNonblank(preferred.email(), fallback.email())
        );
    }

    private Actor actorFrom(JsonNode user) {
        if (user == null || user.isMissingNode() || user.isNull()) {
            return Actor.empty(null);
        }
        return new Actor(
                value(user.path("id").asText(null)),
                value(user.path("name").asText(null)),
                value(user.path("person").path("email").asText(null))
        );
    }

    private String firstNonblank(String preferred, String fallback) {
        String value = value(preferred);
        return value == null ? value(fallback) : value;
    }

    private String value(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static final class Session {

        private final Long projectIntegrationId;
        private final String accessToken;
        private final CollectionContext context;
        private final Map<String, Actor> knownActors = new HashMap<>();
        private final Map<String, Actor> backfilledActors = new HashMap<>();
        private final Set<String> lookupAttempts = new HashSet<>();

        private Session(Long projectIntegrationId, String accessToken, CollectionContext context) {
            this.projectIntegrationId = projectIntegrationId;
            this.accessToken = accessToken;
            this.context = context;
        }

        String accessToken() {
            return accessToken;
        }
    }

    record Actor(String providerId, String login, String email) {

        private static Actor empty(String providerId) {
            return new Actor(providerId, null, null);
        }
    }
}
