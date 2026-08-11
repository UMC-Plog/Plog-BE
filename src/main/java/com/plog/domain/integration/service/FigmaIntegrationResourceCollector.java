package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Figma 파일의 메타데이터, version history, 댓글과 reaction 원문을 저장한다. */
@Slf4j
@Component
class FigmaIntegrationResourceCollector implements IntegrationResourceCollector {

    private static final String API_BASE_URL = "https://api.figma.com";

    private final ProjectIntegrationService projectIntegrationService;
    private final IntegrationActivityStoreService activityStoreService;
    private final RestClient restClient;

    /** 생성자가 둘이라 Spring이 주입 대상을 고를 수 없다. 이쪽이 운영용이다. */
    @Autowired
    FigmaIntegrationResourceCollector(
            ProjectIntegrationService projectIntegrationService,
            IntegrationActivityStoreService activityStoreService
    ) {
        this(projectIntegrationService, activityStoreService, ProviderRestClientFactory.create());
    }

    /** 테스트에서 MockRestServiceServer를 물리기 위한 생성자다. */
    FigmaIntegrationResourceCollector(
            ProjectIntegrationService projectIntegrationService,
            IntegrationActivityStoreService activityStoreService,
            RestClient restClient
    ) {
        this.projectIntegrationService = projectIntegrationService;
        this.activityStoreService = activityStoreService;
        this.restClient = restClient;
    }

    @Override
    public List<LinkType> providers() {
        return List.of(LinkType.FIGMA);
    }

    @Override
    public void collect(
            IntegrationResource resource,
            ProjectIntegration verifiedIntegration,
            CollectionContext context
    ) {
        String token = projectIntegrationService.decryptAccessToken(verifiedIntegration);
        String fileKey = resource.getProviderResourceId();
        JsonNode file = get("/v1/files/" + fileKey + "/meta", token, context).path("file");
        activityStoreService.store(resource, IntegrationActivityType.FIGMA_FILE_METADATA,
                "file:" + fileKey + ":" + file.path("version").asText("current"), null, null, null,
                parseInstant(file.path("last_touched_at").asText(null)), resource.getResourceUrl(),
                file.toString());

        collectVersions(resource, fileKey, token, context);

        JsonNode comments = get("/v1/files/" + fileKey + "/comments", token, context);
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
                        "reaction:" + comment.path("id").asText()
                                + ":" + reactionUser.path("id").asText()
                                + ":" + reaction.path("emoji").asText()
                                + ":" + reaction.path("created_at").asText(),
                        reactionUser.path("id").asText(null), reactionUser.path("handle").asText(null),
                        reactionUser.path("email").asText(null),
                        parseInstant(reaction.path("created_at").asText(null)), resource.getResourceUrl(),
                        reaction.toString());
            }
        }
    }

    private void collectVersions(IntegrationResource resource, String fileKey, String token,
            CollectionContext context) {
        String nextPage = "/v1/files/" + fileKey + "/versions";
        Set<String> requestedPages = new HashSet<>();
        while (nextPage != null && !nextPage.isBlank()) {
            if (!requestedPages.add(nextPage)) {
                log.warn("Figma version pagination loop detected. fileKey={}, page={}", fileKey, nextPage);
                throw new ProviderResourceAccessException(503, null);
            }
            JsonNode response = get(nextPage, token, context);
            for (JsonNode version : response.path("versions")) {
                JsonNode user = version.path("user");
                activityStoreService.store(resource, IntegrationActivityType.FIGMA_FILE_VERSION,
                        "version:" + version.path("id").asText(), user.path("id").asText(null),
                        user.path("handle").asText(null), user.path("email").asText(null),
                        parseInstant(version.path("created_at").asText(null)), resource.getResourceUrl(),
                        version.toString());
            }
            nextPage = response.path("pagination").path("next_page").asText(null);
        }
    }

    private JsonNode get(String path, String token, CollectionContext context) {
        try {
            context.heartbeat();
            return restClient.get()
                    .uri(URI.create(path.startsWith("http") ? path : API_BASE_URL + path))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn("Figma API returned error response. path={}, status={}, body={}",
                    path, exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            throw new ProviderResourceAccessException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            log.warn("Figma API call failed without a response (timeout/connection issue). path={}", path, exception);
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
