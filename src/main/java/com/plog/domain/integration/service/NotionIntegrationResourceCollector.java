package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Notion page/data source의 page·block·comment 원문을 수집한다. 수정 diff가 없으므로 스냅샷을 보관한다. */
@Component
@RequiredArgsConstructor
class NotionIntegrationResourceCollector implements IntegrationResourceCollector {

    private static final String API_BASE_URL = "https://api.notion.com";
    private static final String NOTION_VERSION = "2026-03-11";

    private final ProjectIntegrationService projectIntegrationService;
    private final IntegrationActivityStoreService activityStoreService;
    private final RestClient restClient = ProviderRestClientFactory.create();

    @Override
    public LinkType provider() {
        return LinkType.NOTION;
    }

    @Override
    public void collect(IntegrationResource resource) {
        String token = projectIntegrationService.decryptAccessToken(resource.getProjectIntegration());
        if (resource.getResourceType() == IntegrationResourceType.NOTION_PAGE) {
            collectPage(resource, resource.getProviderResourceId(), token, new HashSet<>());
            return;
        }
        if (resource.getResourceType() == IntegrationResourceType.NOTION_DATA_SOURCE) {
            JsonNode dataSource = get("/v1/data_sources/" + resource.getProviderResourceId(), token);
            activityStoreService.store(resource, IntegrationActivityType.NOTION_DATA_SOURCE_SNAPSHOT,
                    "data-source:" + resource.getProviderResourceId() + ":" + dataSource.path("last_edited_time").asText("current"),
                    dataSource.path("last_edited_by").path("id").asText(null), null, null,
                    parseInstant(dataSource.path("last_edited_time").asText(null)), dataSource.path("url").asText(resource.getResourceUrl()),
                    dataSource.toString());
            String cursor = null;
            do {
                JsonNode page = post("/v1/data_sources/" + resource.getProviderResourceId() + "/query", token,
                        cursor == null
                                ? Map.of("page_size", 100, "result_type", "page")
                                : Map.of("page_size", 100, "result_type", "page", "start_cursor", cursor));
                for (JsonNode result : page.path("results")) {
                    String pageId = result.path("id").asText();
                    if (!pageId.isBlank()) {
                        collectPage(resource, pageId, token, new HashSet<>());
                    }
                }
                cursor = page.path("has_more").asBoolean(false) ? page.path("next_cursor").asText(null) : null;
            } while (cursor != null && !cursor.isBlank());
        }
    }

    private void collectPage(IntegrationResource resource, String pageId, String token, Set<String> visitedBlocks) {
        JsonNode page = get("/v1/pages/" + pageId, token);
        JsonNode createdBy = page.path("created_by");
        JsonNode editedBy = page.path("last_edited_by");
        activityStoreService.store(resource, IntegrationActivityType.NOTION_PAGE_SNAPSHOT,
                "page:" + pageId + ":" + page.path("last_edited_time").asText("current"),
                editedBy.path("id").asText(createdBy.path("id").asText(null)), null, null,
                parseInstant(page.path("last_edited_time").asText(page.path("created_time").asText(null))),
                page.path("url").asText(resource.getResourceUrl()), page.toString());
        collectBlocks(resource, pageId, token, visitedBlocks);
        collectComments(resource, pageId, token);
    }

    private void collectBlocks(IntegrationResource resource, String blockId, String token, Set<String> visitedBlocks) {
        if (!visitedBlocks.add(blockId)) {
            return;
        }
        String cursor = null;
        do {
            String path = "/v1/blocks/" + blockId + "/children?page_size=100"
                    + (cursor == null ? "" : "&start_cursor=" + cursor);
            JsonNode body = get(path, token);
            for (JsonNode block : body.path("results")) {
                String id = block.path("id").asText();
                JsonNode createdBy = block.path("created_by");
                JsonNode editedBy = block.path("last_edited_by");
                activityStoreService.store(resource, IntegrationActivityType.NOTION_BLOCK_SNAPSHOT,
                        "block:" + id + ":" + block.path("last_edited_time").asText("current"),
                        editedBy.path("id").asText(createdBy.path("id").asText(null)), null, null,
                        parseInstant(block.path("last_edited_time").asText(block.path("created_time").asText(null))),
                        resource.getResourceUrl(), block.toString());
                if (!id.isBlank()) {
                    collectComments(resource, id, token);
                }
                if (block.path("has_children").asBoolean(false) && !id.isBlank()) {
                    collectBlocks(resource, id, token, visitedBlocks);
                }
            }
            cursor = body.path("has_more").asBoolean(false) ? body.path("next_cursor").asText(null) : null;
        } while (cursor != null && !cursor.isBlank());
    }

    private void collectComments(IntegrationResource resource, String pageId, String token) {
        String cursor = null;
        do {
            String path = "/v1/comments?block_id=" + pageId + "&page_size=100"
                    + (cursor == null ? "" : "&start_cursor=" + cursor);
            JsonNode body = get(path, token);
            for (JsonNode comment : body.path("results")) {
                JsonNode author = comment.path("created_by");
                activityStoreService.store(resource, IntegrationActivityType.NOTION_COMMENT,
                        "comment:" + comment.path("id").asText(), author.path("id").asText(null), null, null,
                        parseInstant(comment.path("created_time").asText(null)), resource.getResourceUrl(), comment.toString());
            }
            cursor = body.path("has_more").asBoolean(false) ? body.path("next_cursor").asText(null) : null;
        } while (cursor != null && !cursor.isBlank());
    }

    private JsonNode get(String path, String token) {
        try {
            return restClient.get().uri(API_BASE_URL + path)
                    .header("Notion-Version", NOTION_VERSION)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ProviderResourceAccessException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new ProviderResourceAccessException(503, exception);
        }
    }

    private JsonNode post(String path, String token, Object request) {
        try {
            return restClient.post().uri(API_BASE_URL + path)
                    .header("Notion-Version", NOTION_VERSION)
                    .headers(headers -> headers.setBearerAuth(token))
                    .body(request)
                    .retrieve().body(JsonNode.class);
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
