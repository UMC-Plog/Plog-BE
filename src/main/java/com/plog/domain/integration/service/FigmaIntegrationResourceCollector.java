package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import java.net.URI;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Figma 파일의 현재 스냅샷, version history, 댓글과 reaction 원문을 저장한다. */
@Component
@RequiredArgsConstructor
class FigmaIntegrationResourceCollector implements IntegrationResourceCollector {

    private static final String API_BASE_URL = "https://api.figma.com";

    private final ProjectIntegrationService projectIntegrationService;
    private final IntegrationActivityStoreService activityStoreService;
    private final RestClient restClient = ProviderRestClientFactory.create();

    @Override
    public LinkType provider() {
        return LinkType.FIGMA;
    }

    @Override
    public void collect(IntegrationResource resource) {
        String token = projectIntegrationService.decryptAccessToken(resource.getProjectIntegration());
        String fileKey = resource.getProviderResourceId();
        JsonNode file = get("/v1/files/" + fileKey + "?depth=1", token);
        activityStoreService.store(resource, IntegrationActivityType.FIGMA_FILE_METADATA,
                "file:" + fileKey + ":" + file.path("version").asText("current"), null, null, null,
                parseInstant(file.path("lastModified").asText(null)), resource.getResourceUrl(), file.toString());

        JsonNode versions = get("/v1/files/" + fileKey + "/versions", token);
        for (JsonNode version : versions.path("versions")) {
            JsonNode user = version.path("user");
            activityStoreService.store(resource, IntegrationActivityType.FIGMA_FILE_VERSION,
                    "version:" + version.path("id").asText(), user.path("id").asText(null),
                    user.path("handle").asText(null), user.path("email").asText(null),
                    parseInstant(version.path("created_at").asText(null)), resource.getResourceUrl(),
                    version.toString());
        }

        JsonNode comments = get("/v1/files/" + fileKey + "/comments", token);
        for (JsonNode comment : comments.path("comments")) {
            JsonNode user = comment.path("user");
            activityStoreService.store(resource, IntegrationActivityType.FIGMA_COMMENT,
                    "comment:" + comment.path("id").asText(), user.path("id").asText(null),
                    user.path("handle").asText(null), user.path("email").asText(null),
                    parseInstant(comment.path("created_at").asText(null)), resource.getResourceUrl(),
                    comment.toString());
            for (JsonNode reaction : comment.path("reactions")) {
                JsonNode reactionUser = reaction.path("user");
                activityStoreService.store(resource, IntegrationActivityType.FIGMA_COMMENT_REACTION,
                        "reaction:" + comment.path("id").asText() + ":" + reaction.path("id").asText(),
                        reactionUser.path("id").asText(null), reactionUser.path("handle").asText(null),
                        reactionUser.path("email").asText(null),
                        parseInstant(reaction.path("created_at").asText(null)), resource.getResourceUrl(),
                        reaction.toString());
            }
        }
    }

    private JsonNode get(String path, String token) {
        try {
            return restClient.get()
                    .uri(URI.create(API_BASE_URL + path))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ProviderResourceAccessException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new ProviderResourceAccessException(503, exception);
        }
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
