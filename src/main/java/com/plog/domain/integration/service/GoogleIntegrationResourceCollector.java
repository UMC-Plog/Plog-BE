package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Google Drive Activity, comment/revision과 Docs/Slides 현재 스냅샷을 저장한다. */
@Slf4j
@Component
class GoogleIntegrationResourceCollector implements IntegrationResourceCollector {

    private static final String DRIVE_API = "https://www.googleapis.com/drive/v3";
    private static final String DRIVE_ACTIVITY_API = "https://driveactivity.googleapis.com/v2";
    private static final String DOCS_API = "https://docs.googleapis.com/v1";
    private static final String SLIDES_API = "https://slides.googleapis.com/v1";

    private final ProjectIntegrationService projectIntegrationService;
    private final IntegrationActivityStoreService activityStoreService;
    private final RestClient restClient;

    /** 생성자가 둘이라 Spring이 주입 대상을 고를 수 없다. 이쪽이 운영용이다. */
    @Autowired
    GoogleIntegrationResourceCollector(
            ProjectIntegrationService projectIntegrationService,
            IntegrationActivityStoreService activityStoreService
    ) {
        this(projectIntegrationService, activityStoreService, ProviderRestClientFactory.create());
    }

    /** 테스트에서 MockRestServiceServer를 물리기 위한 생성자다. */
    GoogleIntegrationResourceCollector(
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
        return List.of(LinkType.GOOGLE_DOCS, LinkType.GOOGLE_SLIDES);
    }

    @Override
    public void collect(
            IntegrationResource resource,
            ProjectIntegration verifiedIntegration,
            CollectionContext context
    ) {
        String token = projectIntegrationService.decryptAccessToken(verifiedIntegration);
        String fileId = resource.getProviderResourceId();
        collectFileMetadata(resource, fileId, token, context);
        collectOptional(() -> collectDriveActivity(resource, fileId, token, context));
        collectOptional(() -> collectComments(resource, fileId, token, context));
        collectOptional(() -> collectRevisions(resource, fileId, token, context));
        if (resource.getResourceType() == IntegrationResourceType.GOOGLE_DOCUMENT) {
            collectOptional(() -> collectDocumentSnapshot(resource, fileId, token, context));
        }
        if (resource.getResourceType() == IntegrationResourceType.GOOGLE_PRESENTATION) {
            collectOptional(() -> collectPresentationSnapshot(resource, fileId, token, context));
        }
    }

    private void collectDocumentSnapshot(IntegrationResource resource, String fileId, String token,
            CollectionContext context) {
        JsonNode document = get(DOCS_API + "/documents/" + fileId
                + "?suggestionsViewMode=SUGGESTIONS_INLINE&includeTabsContent=true", token, context);
        activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DOCUMENT_SUGGESTION,
                "document-snapshot:" + fileId + ":" + snapshotVersion(document), null, null, null,
                null, resource.getResourceUrl(), document.toString());
    }

    private void collectPresentationSnapshot(IntegrationResource resource, String fileId, String token,
            CollectionContext context) {
        JsonNode presentation = get(SLIDES_API + "/presentations/" + fileId, token, context);
        activityStoreService.store(resource, IntegrationActivityType.GOOGLE_PRESENTATION_SNAPSHOT,
                "presentation-snapshot:" + fileId + ":" + snapshotVersion(presentation), null, null, null, null,
                resource.getResourceUrl(), presentation.toString());
    }

    /** 파일 메타데이터 확인 뒤에는 provider별 부가 API의 404가 리소스 삭제를 의미하지 않는다. */
    private void collectOptional(Runnable collection) {
        try {
            collection.run();
        } catch (ProviderResourceAccessException exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
        }
    }

    private void collectFileMetadata(IntegrationResource resource, String fileId, String token,
            CollectionContext context) {
        JsonNode file = get(DRIVE_API + "/files/" + fileId
                + "?fields=id,name,mimeType,createdTime,modifiedTime,lastModifyingUser,owners,webViewLink",
                token, context);
        JsonNode actor = file.path("lastModifyingUser");
        activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DRIVE_FILE_SNAPSHOT,
                "drive-file:" + fileId + ":" + file.path("modifiedTime").asText("current"),
                actor.path("permissionId").asText(null), actor.path("displayName").asText(null),
                actor.path("emailAddress").asText(null),
                parseInstant(file.path("modifiedTime").asText(file.path("createdTime").asText(null))),
                file.path("webViewLink").asText(resource.getResourceUrl()), file.toString());
    }

    private void collectDriveActivity(IntegrationResource resource, String fileId, String token,
            CollectionContext context) {
        String pageToken = null;
        Set<String> requestedPageTokens = new HashSet<>();
        do {
            JsonNode body = post(DRIVE_ACTIVITY_API + "/activity:query", token,
                    pageToken == null
                            ? Map.of("itemName", "items/" + fileId, "pageSize", 100)
                            : Map.of("itemName", "items/" + fileId, "pageSize", 100, "pageToken", pageToken),
                    context);
            for (JsonNode activity : body.path("activities")) {
                JsonNode actor = activity.path("actors").isArray() && !activity.path("actors").isEmpty()
                        ? activity.path("actors").get(0)
                        : null;
                JsonNode knownUser = actor == null ? null : actor.path("user").path("knownUser");
                activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY,
                        "drive-activity:" + sha256(canonicalJson(activity)),
                        text(knownUser, "personName"), firstText(knownUser, "displayName", "name"),
                        firstText(knownUser, "emailAddress", "email"),
                        parseInstant(activity.path("timestamp")
                                .asText(activity.path("timeRange").path("endTime").asText(null))),
                        resource.getResourceUrl(), activity.toString());
            }
            pageToken = nextPageToken(body, requestedPageTokens, "drive-activity", fileId);
        } while (pageToken != null && !pageToken.isBlank());
    }

    private void collectComments(IntegrationResource resource, String fileId, String token,
            CollectionContext context) {
        String pageToken = null;
        Set<String> requestedPageTokens = new HashSet<>();
        do {
            String url = DRIVE_API + "/files/" + fileId
                    + "/comments?pageSize=100&fields=nextPageToken,comments(id,createdTime,modifiedTime,author,content,resolved,replies)"
                    + (pageToken == null ? "" : "&pageToken=" + pageToken);
            JsonNode body = get(url, token, context);
            for (JsonNode comment : body.path("comments")) {
                JsonNode author = comment.path("author");
                activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DRIVE_COMMENT,
                        "comment:" + comment.path("id").asText(), author.path("permissionId").asText(null),
                        author.path("displayName").asText(null), author.path("emailAddress").asText(null),
                        parseInstant(comment.path("createdTime").asText(null)), resource.getResourceUrl(),
                        comment.toString());
                for (JsonNode reply : comment.path("replies")) {
                    JsonNode replyAuthor = reply.path("author");
                    activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DRIVE_COMMENT,
                            "comment-reply:" + reply.path("id").asText(),
                            replyAuthor.path("permissionId").asText(null),
                            replyAuthor.path("displayName").asText(null),
                            replyAuthor.path("emailAddress").asText(null),
                            parseInstant(reply.path("createdTime").asText(null)), resource.getResourceUrl(),
                            reply.toString());
                }
            }
            pageToken = nextPageToken(body, requestedPageTokens, "drive-comments", fileId);
        } while (pageToken != null && !pageToken.isBlank());
    }

    private void collectRevisions(IntegrationResource resource, String fileId, String token,
            CollectionContext context) {
        String pageToken = null;
        Set<String> requestedPageTokens = new HashSet<>();
        do {
            String url = DRIVE_API + "/files/" + fileId
                    + "/revisions?pageSize=100&fields=nextPageToken,revisions(id,modifiedTime,lastModifyingUser,originalFilename,mimeType)"
                    + (pageToken == null ? "" : "&pageToken=" + pageToken);
            JsonNode body = get(url, token, context);
            for (JsonNode revision : body.path("revisions")) {
                JsonNode author = revision.path("lastModifyingUser");
                activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DRIVE_REVISION,
                        "revision:" + revision.path("id").asText(), author.path("permissionId").asText(null),
                        author.path("displayName").asText(null), author.path("emailAddress").asText(null),
                        parseInstant(revision.path("modifiedTime").asText(null)), resource.getResourceUrl(),
                        revision.toString());
            }
            pageToken = nextPageToken(body, requestedPageTokens, "drive-revisions", fileId);
        } while (pageToken != null && !pageToken.isBlank());
    }

    private String nextPageToken(JsonNode response, Set<String> requestedPageTokens, String context, String fileId) {
        String nextPageToken = response.path("nextPageToken").asText(null);
        if (nextPageToken == null || nextPageToken.isBlank()) {
            return null;
        }
        if (!requestedPageTokens.add(nextPageToken)) {
            log.warn("Google API pagination loop detected. fileId={}, context={}", fileId, context);
            throw new ProviderResourceAccessException(503, null);
        }
        return nextPageToken;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.path(field).isMissingNode()) {
            return null;
        }
        return node.path(field).asText(null);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String snapshotVersion(JsonNode snapshot) {
        String revisionId = snapshot.path("revisionId").asText(null);
        return revisionId == null || revisionId.isBlank()
                ? sha256(canonicalJson(snapshot))
                : revisionId;
    }

    private String canonicalJson(JsonNode node) {
        if (node.isObject()) {
            ArrayList<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            StringBuilder builder = new StringBuilder("{");
            for (int index = 0; index < fieldNames.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                String fieldName = fieldNames.get(index);
                builder.append(JsonNodeFactory.instance.textNode(fieldName))
                        .append(':')
                        .append(canonicalJson(node.get(fieldName)));
            }
            return builder.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(canonicalJson(node.get(index)));
            }
            return builder.append(']').toString();
        }
        return node.toString();
    }

    private JsonNode get(String uri, String token, CollectionContext context) {
        try {
            context.heartbeat();
            return restClient.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            logProviderErrorResponse("Google", uri, exception);
            throw new ProviderResourceAccessException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            log.warn("Google API call failed without a response (timeout/connection issue). uri={}", uri, exception);
            throw new ProviderResourceAccessException(503, exception);
        }
    }

    private JsonNode post(String uri, String token, Object request, CollectionContext context) {
        try {
            context.heartbeat();
            return restClient.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            logProviderErrorResponse("Google", uri, exception);
            throw new ProviderResourceAccessException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            log.warn("Google API call failed without a response (timeout/connection issue). uri={}", uri, exception);
            throw new ProviderResourceAccessException(503, exception);
        }
    }

    /** 404는 collectOptional에서 정상적으로 무시되는 흐름이 많아 DEBUG로, 그 외는 WARN으로 남긴다. */
    private void logProviderErrorResponse(String provider, String uri, RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String sanitizedBody = ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString());
        if (status == 404) {
            log.debug("{} API returned 404 (may be expected for optional resources). uri={}", provider, uri);
            return;
        }
        log.warn("{} API returned error response. uri={}, status={}, body={}", provider, uri, status, sanitizedBody);
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
