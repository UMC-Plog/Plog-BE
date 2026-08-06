package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Notion page/data source의 page·block·comment 원문을 수집한다. 수정 diff가 없으므로 스냅샷을 보관한다. */
@Slf4j
@Component
class NotionIntegrationResourceCollector implements IntegrationResourceCollector {

    private static final String API_BASE_URL = "https://api.notion.com";
    private static final String NOTION_VERSION = "2026-03-11";

    private final ProjectIntegrationService projectIntegrationService;
    private final IntegrationActivityStoreService activityStoreService;
    private final NotionApiRateLimiter rateLimiter;
    private final RestClient restClient;

    /** 생성자가 둘이라 Spring이 주입 대상을 고를 수 없다. 이쪽이 운영용이다. */
    @Autowired
    NotionIntegrationResourceCollector(
            ProjectIntegrationService projectIntegrationService,
            IntegrationActivityStoreService activityStoreService,
            NotionApiRateLimiter rateLimiter
    ) {
        this(projectIntegrationService, activityStoreService, rateLimiter, ProviderRestClientFactory.create());
    }

    /** 테스트에서 MockRestServiceServer를 물리기 위한 생성자다. */
    NotionIntegrationResourceCollector(
            ProjectIntegrationService projectIntegrationService,
            IntegrationActivityStoreService activityStoreService,
            NotionApiRateLimiter rateLimiter,
            RestClient restClient
    ) {
        this.projectIntegrationService = projectIntegrationService;
        this.activityStoreService = activityStoreService;
        this.rateLimiter = rateLimiter;
        this.restClient = restClient;
    }

    @Override
    public List<LinkType> providers() {
        return List.of(LinkType.NOTION);
    }

    @Override
    public void collect(IntegrationResource resource, CollectionContext context) {
        String token = projectIntegrationService.decryptAccessToken(resource.getProjectIntegration());
        collect(resource, token, context);
    }

    private void collect(IntegrationResource resource, String token, CollectionContext context) {
        if (resource.getResourceType() == IntegrationResourceType.NOTION_PAGE) {
            collectPage(resource, resource.getProviderResourceId(), token, context, new HashSet<>());
            return;
        }
        if (resource.getResourceType() == IntegrationResourceType.NOTION_DATA_SOURCE) {
            JsonNode dataSource = get("/v1/data_sources/" + resource.getProviderResourceId(), token, context);
            JsonNode editor = dataSource.path("last_edited_by");
            activityStoreService.store(resource, IntegrationActivityType.NOTION_DATA_SOURCE_SNAPSHOT,
                    "data-source:" + resource.getProviderResourceId() + ":" + dataSource.path("last_edited_time").asText("current"),
                    actorId(editor), actorName(editor), actorEmail(editor),
                    parseInstant(dataSource.path("last_edited_time").asText(null)), dataSource.path("url").asText(resource.getResourceUrl()),
                    dataSource.toString());
            String cursor = null;
            Set<String> requestedCursors = new HashSet<>();
            do {
                JsonNode page = post("/v1/data_sources/" + resource.getProviderResourceId() + "/query", token, context,
                        cursor == null
                                ? Map.of("page_size", 100, "result_type", "page")
                                : Map.of("page_size", 100, "result_type", "page", "start_cursor", cursor));
                for (JsonNode result : page.path("results")) {
                    String pageId = result.path("id").asText();
                    if (!pageId.isBlank()) {
                        collectPage(resource, pageId, token, context, new HashSet<>());
                    }
                }
                cursor = nextCursor(page, requestedCursors, "data-source-query", resource.getProviderResourceId());
            } while (cursor != null && !cursor.isBlank());
        }
    }

    IntegrationResource findContainingResource(
            List<IntegrationResource> resources,
            NotionWebhookTarget target,
            CollectionContext context
    ) {
        if (resources.isEmpty()) {
            return null;
        }
        Map<String, IntegrationResource> resourcesByProviderId = new HashMap<>();
        for (IntegrationResource resource : resources) {
            resourcesByProviderId.put(resource.getProviderResourceId(), resource);
        }
        IntegrationResource direct = resourcesByProviderId.get(target.entityId());
        if (direct != null) {
            return direct;
        }

        String token = projectIntegrationService.decryptAccessToken(resources.getFirst().getProjectIntegration());
        ParentRef current = target.parentId() == null
                ? new ParentRef(normalizeType(target.entityType()), target.entityId())
                : new ParentRef(normalizeType(target.parentType()), target.parentId());
        Set<String> visited = new HashSet<>();
        for (int depth = 0; current != null && depth < 100; depth++) {
            IntegrationResource matched = resourcesByProviderId.get(current.id());
            if (matched != null) {
                return matched;
            }
            if (!visited.add(current.type() + ":" + current.id())) {
                return null;
            }
            current = parentOf(current, token, context);
        }
        return null;
    }

    void collectChangedEntity(IntegrationResource resource, NotionWebhookTarget target, CollectionContext context) {
        String token = projectIntegrationService.decryptAccessToken(resource.getProjectIntegration());
        String type = normalizeType(target.entityType());
        if ("page".equals(type)) {
            collectPage(resource, target.entityId(), token, context, new HashSet<>());
            return;
        }
        if ("block".equals(type)) {
            collectBlock(resource, target.entityId(), token, context, new HashSet<>());
            return;
        }
        if ("comment".equals(type) && target.parentId() != null) {
            collectComments(resource, target.parentId(), token, context);
            return;
        }
        collect(resource, token, context);
    }

    private void collectPage(
            IntegrationResource resource,
            String pageId,
            String token,
            CollectionContext context,
            Set<String> visitedBlocks
    ) {
        JsonNode page = get("/v1/pages/" + pageId, token, context);
        JsonNode createdBy = page.path("created_by");
        JsonNode editedBy = page.path("last_edited_by");
        JsonNode actor = preferredActor(editedBy, createdBy);
        activityStoreService.store(resource, IntegrationActivityType.NOTION_PAGE_SNAPSHOT,
                "page:" + pageId + ":" + page.path("last_edited_time").asText("current"),
                actorId(actor), actorName(actor), actorEmail(actor),
                parseInstant(page.path("last_edited_time").asText(page.path("created_time").asText(null))),
                page.path("url").asText(resource.getResourceUrl()), page.toString());
        collectBlocks(resource, pageId, token, context, visitedBlocks);
        collectComments(resource, pageId, token, context);
    }

    private void collectBlocks(
            IntegrationResource resource,
            String blockId,
            String token,
            CollectionContext context,
            Set<String> visitedBlocks
    ) {
        if (!visitedBlocks.add(blockId)) {
            return;
        }
        String cursor = null;
        Set<String> requestedCursors = new HashSet<>();
        do {
            String path = "/v1/blocks/" + blockId + "/children?page_size=100"
                    + (cursor == null ? "" : "&start_cursor=" + cursor);
            JsonNode body = get(path, token, context);
            for (JsonNode block : body.path("results")) {
                String id = block.path("id").asText();
                storeBlock(resource, block);
                if (!id.isBlank()) {
                    collectComments(resource, id, token, context);
                }
                if (block.path("has_children").asBoolean(false) && !id.isBlank()) {
                    collectBlocks(resource, id, token, context, visitedBlocks);
                }
            }
            cursor = nextCursor(body, requestedCursors, "block-children", blockId);
        } while (cursor != null && !cursor.isBlank());
    }

    private void collectBlock(
            IntegrationResource resource,
            String blockId,
            String token,
            CollectionContext context,
            Set<String> visitedBlocks
    ) {
        if (!visitedBlocks.add(blockId)) {
            return;
        }
        JsonNode block = get("/v1/blocks/" + blockId, token, context);
        storeBlock(resource, block);
        collectComments(resource, blockId, token, context);
        if (block.path("has_children").asBoolean(false)) {
            collectBlocks(resource, blockId, token, context, visitedBlocks);
        }
    }

    private void collectComments(IntegrationResource resource, String pageId, String token, CollectionContext context) {
        String cursor = null;
        Set<String> requestedCursors = new HashSet<>();
        do {
            String path = "/v1/comments?block_id=" + pageId + "&page_size=100"
                    + (cursor == null ? "" : "&start_cursor=" + cursor);
            JsonNode body = get(path, token, context);
            for (JsonNode comment : body.path("results")) {
                JsonNode author = comment.path("created_by");
                activityStoreService.store(resource, IntegrationActivityType.NOTION_COMMENT,
                        "comment:" + comment.path("id").asText(), actorId(author), actorName(author), actorEmail(author),
                        parseInstant(comment.path("created_time").asText(null)), resource.getResourceUrl(), comment.toString());
            }
            cursor = nextCursor(body, requestedCursors, "page-comments", pageId);
        } while (cursor != null && !cursor.isBlank());
    }

    private String nextCursor(JsonNode body, Set<String> requestedCursors, String context, String parentId) {
        boolean hasMore = body.path("has_more").asBoolean(false);
        String cursor = hasMore ? body.path("next_cursor").asText(null) : null;
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        if (!requestedCursors.add(cursor)) {
            log.warn("Notion API pagination loop detected. parentId={}, context={}", parentId, context);
            throw new ProviderResourceAccessException(503, null);
        }
        return cursor;
    }

    private JsonNode get(String path, String token, CollectionContext context) {
        try {
            context.heartbeat();
            rateLimiter.acquire();
            return restClient.get().uri(API_BASE_URL + path)
                    .header("Notion-Version", NOTION_VERSION)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn("Notion API returned error response. path={}, status={}, body={}",
                    path, exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            throw new ProviderResourceAccessException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            log.warn("Notion API call failed without a response (timeout/connection issue). path={}", path, exception);
            throw new ProviderResourceAccessException(503, exception);
        }
    }

    private JsonNode preferredActor(JsonNode preferred, JsonNode fallback) {
        return preferred.path("id").isMissingNode() || preferred.path("id").asText().isBlank()
                ? fallback : preferred;
    }

    private String actorId(JsonNode actor) {
        return actor.path("id").asText(null);
    }

    private String actorName(JsonNode actor) {
        return actor.path("name").asText(null);
    }

    private String actorEmail(JsonNode actor) {
        return actor.path("person").path("email").asText(null);
    }

    private JsonNode post(String path, String token, CollectionContext context, Object request) {
        try {
            context.heartbeat();
            rateLimiter.acquire();
            return restClient.post().uri(API_BASE_URL + path)
                    .header("Notion-Version", NOTION_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(token))
                    .body(request)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn("Notion API returned error response. path={}, status={}, body={}",
                    path, exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            throw new ProviderResourceAccessException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            log.warn("Notion API call failed without a response (timeout/connection issue). path={}", path, exception);
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

    private void storeBlock(IntegrationResource resource, JsonNode block) {
        String id = block.path("id").asText();
        JsonNode actor = preferredActor(block.path("last_edited_by"), block.path("created_by"));
        activityStoreService.store(resource, IntegrationActivityType.NOTION_BLOCK_SNAPSHOT,
                "block:" + id + ":" + block.path("last_edited_time").asText("current"),
                actorId(actor), actorName(actor), actorEmail(actor),
                parseInstant(block.path("last_edited_time").asText(block.path("created_time").asText(null))),
                resource.getResourceUrl(), block.toString());
    }

    private ParentRef parentOf(ParentRef current, String token, CollectionContext context) {
        String path = switch (current.type()) {
            case "page" -> "/v1/pages/" + current.id();
            case "block" -> "/v1/blocks/" + current.id();
            case "data_source" -> "/v1/data_sources/" + current.id();
            case "database" -> "/v1/databases/" + current.id();
            default -> null;
        };
        if (path == null) {
            return null;
        }
        JsonNode parent = get(path, token, context).path("parent");
        String type = normalizeType(parent.path("type").asText(null));
        if (type == null || "workspace".equals(type)) {
            return null;
        }
        String id = parent.path("id").asText(null);
        if (id == null || id.isBlank()) {
            id = parent.path(type + "_id").asText(null);
        }
        return id == null || id.isBlank() ? null : new ParentRef(type, id);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.toLowerCase().replace('-', '_');
        return normalized.endsWith("_id")
                ? normalized.substring(0, normalized.length() - 3)
                : normalized;
    }

    private record ParentRef(String type, String id) {
    }
}
