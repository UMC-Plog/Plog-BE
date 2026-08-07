package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** Drive Activity의 People resource name을 표시 가능한 actor 정보로 보강한다. */
@Slf4j
final class GooglePeopleActorResolver {

    private static final String PEOPLE_BATCH_GET_API =
            "https://people.googleapis.com/v1/people:batchGet";
    private static final int MAX_BATCH_SIZE = 200;

    private final RestClient restClient;

    GooglePeopleActorResolver(RestClient restClient) {
        this.restClient = restClient;
    }

    Session begin(String accessToken, CollectionContext context) {
        return new Session(accessToken, context);
    }

    void prefetch(Session session, List<JsonNode> knownUsers) {
        LinkedHashSet<String> requestedNames = new LinkedHashSet<>();
        for (JsonNode knownUser : knownUsers) {
            Actor partial = actorFromKnownUser(knownUser);
            if (partial.providerId() == null) {
                continue;
            }
            session.knownActors.merge(partial.providerId(), partial, this::merge);
            Actor known = session.knownActors.get(partial.providerId());
            if (session.failure == null
                    && needsLookup(known)
                    && session.lookupAttempts.add(partial.providerId())) {
                requestedNames.add(partial.providerId());
            }
        }

        List<String> names = new ArrayList<>(requestedNames);
        for (int start = 0; start < names.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, names.size());
            retrieve(session, names.subList(start, end));
            if (session.failure != null) {
                break;
            }
        }
    }

    Actor resolve(Session session, JsonNode knownUser) {
        Actor partial = actorFromKnownUser(knownUser);
        if (partial.providerId() == null) {
            return partial;
        }
        return merge(session.knownActors.get(partial.providerId()), partial);
    }

    void throwIfFailed(Session session) {
        if (session.failure != null) {
            throw session.failure;
        }
    }

    private void retrieve(Session session, List<String> resourceNames) {
        if (resourceNames.isEmpty()) {
            return;
        }
        URI uri = peopleBatchGetUri(resourceNames);
        try {
            session.context.heartbeat();
            JsonNode response = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                return;
            }
            for (JsonNode personResponse : response.path("responses")) {
                Actor actor = actorFromPersonResponse(personResponse);
                if (actor.providerId() != null) {
                    session.knownActors.merge(
                            actor.providerId(), actor,
                            (existing, retrieved) -> merge(retrieved, existing)
                    );
                }
            }
        } catch (RestClientResponseException exception) {
            log.warn("Google People actor lookup failed. status={}, body={}",
                    exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            session.failure = collectionFailure(exception);
        } catch (RestClientException exception) {
            log.warn("Google People actor lookup failed without a response.", exception);
            session.failure = new ProviderResourceAccessException(503, exception);
        }
    }

    /** People의 4xx는 Drive 파일 권한·OAuth 폐기를 뜻하지 않으므로 리소스 비활성화/재인가와 분리한다. */
    private ProviderResourceAccessException collectionFailure(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        ProviderResourceAccessException original = new ProviderResourceAccessException(statusCode, exception);
        if (statusCode == 401 || statusCode == 429
                || (statusCode == 403 && ProviderRateLimitSupport.isRateLimited(original))) {
            return original;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return new ProviderResourceAccessException(424, exception);
        }
        return original;
    }

    private URI peopleBatchGetUri(List<String> resourceNames) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(PEOPLE_BATCH_GET_API)
                .queryParam("personFields", "names,emailAddresses")
                .queryParam("sources", "READ_SOURCE_TYPE_PROFILE");
        resourceNames.forEach(resourceName -> builder.queryParam("resourceNames", resourceName));
        return builder.build().encode().toUri();
    }

    private Actor actorFromKnownUser(JsonNode knownUser) {
        if (knownUser == null || knownUser.isMissingNode() || knownUser.isNull()) {
            return Actor.empty(null);
        }
        return new Actor(
                value(knownUser.path("personName").asText(null)),
                firstText(knownUser, "displayName", "name"),
                firstText(knownUser, "emailAddress", "email")
        );
    }

    private Actor actorFromPersonResponse(JsonNode response) {
        JsonNode person = response.path("person");
        String requestedResourceName = value(response.path("requestedResourceName").asText(null));
        return new Actor(
                requestedResourceName == null
                        ? value(person.path("resourceName").asText(null))
                        : requestedResourceName,
                primaryText(person.path("names"), "displayName"),
                primaryText(person.path("emailAddresses"), "value")
        );
    }

    private String primaryText(JsonNode values, String field) {
        if (!values.isArray()) {
            return null;
        }
        for (JsonNode item : values) {
            if (item.path("metadata").path("primary").asBoolean(false)) {
                String primary = value(item.path(field).asText(null));
                if (primary != null) {
                    return primary;
                }
            }
        }
        for (JsonNode item : values) {
            String candidate = value(item.path(field).asText(null));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String candidate = value(node.path(field).asText(null));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private boolean needsLookup(Actor actor) {
        return actor == null || actor.login() == null || actor.email() == null;
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

    private String firstNonblank(String preferred, String fallback) {
        String value = value(preferred);
        return value == null ? value(fallback) : value;
    }

    private String value(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static final class Session {

        private final String accessToken;
        private final CollectionContext context;
        private final Map<String, Actor> knownActors = new HashMap<>();
        private final Set<String> lookupAttempts = new HashSet<>();
        private ProviderResourceAccessException failure;

        private Session(String accessToken, CollectionContext context) {
            this.accessToken = accessToken;
            this.context = context;
        }
    }

    record Actor(String providerId, String login, String email) {

        boolean hasDisplayInformation() {
            return login != null || email != null;
        }

        private static Actor empty(String providerId) {
            return new Actor(providerId, null, null);
        }
    }
}
