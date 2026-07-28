package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Google Drive Activity, comment/revision과 Docs/Slides 현재 스냅샷을 저장한다. */
@Component
@RequiredArgsConstructor
class GoogleIntegrationResourceCollector implements IntegrationResourceCollector {

    private static final String DRIVE_API = "https://www.googleapis.com/drive/v3";
    private static final String DRIVE_ACTIVITY_API = "https://www.googleapis.com/drive/activity/v2";
    private static final String DOCS_API = "https://docs.googleapis.com/v1";
    private static final String SLIDES_API = "https://slides.googleapis.com/v1";

    private final ProjectIntegrationService projectIntegrationService;
    private final IntegrationActivityStoreService activityStoreService;
    private final RestClient restClient = ProviderRestClientFactory.create();

    @Override
    public LinkType provider() {
        return LinkType.GOOGLE;
    }

    @Override
    public void collect(IntegrationResource resource) {
        String token = projectIntegrationService.decryptAccessToken(resource.getProjectIntegration());
        String fileId = resource.getProviderResourceId();
        collectFileMetadata(resource, fileId, token);
        collectOptional(() -> collectDriveActivity(resource, fileId, token));
        collectOptional(() -> collectComments(resource, fileId, token));
        collectOptional(() -> collectRevisions(resource, fileId, token));
        if (resource.getResourceType() == IntegrationResourceType.GOOGLE_DOCUMENT) {
            collectOptional(() -> collectDocumentSnapshot(resource, fileId, token));
        }
        if (resource.getResourceType() == IntegrationResourceType.GOOGLE_PRESENTATION) {
            collectOptional(() -> collectPresentationSnapshot(resource, fileId, token));
        }
    }

    private void collectDocumentSnapshot(IntegrationResource resource, String fileId, String token) {
        JsonNode document = get(DOCS_API + "/documents/" + fileId + "?suggestionsViewMode=PREVIEW_WITH_SUGGESTIONS", token);
        activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DOCUMENT_SUGGESTION,
                "document-snapshot:" + fileId + ":" + document.path("revisionId").asText("current"), null, null, null,
                null, resource.getResourceUrl(), document.toString());
    }

    private void collectPresentationSnapshot(IntegrationResource resource, String fileId, String token) {
        JsonNode presentation = get(SLIDES_API + "/presentations/" + fileId, token);
        activityStoreService.store(resource, IntegrationActivityType.GOOGLE_PRESENTATION_SNAPSHOT,
                "presentation-snapshot:" + fileId, null, null, null, null,
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

    private void collectFileMetadata(IntegrationResource resource, String fileId, String token) {
        JsonNode file = get(DRIVE_API + "/files/" + fileId
                + "?fields=id,name,mimeType,createdTime,modifiedTime,lastModifyingUser,owners,webViewLink", token);
        JsonNode actor = file.path("lastModifyingUser");
        activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DRIVE_FILE_SNAPSHOT,
                "drive-file:" + fileId + ":" + file.path("modifiedTime").asText("current"),
                actor.path("permissionId").asText(null), actor.path("displayName").asText(null),
                actor.path("emailAddress").asText(null),
                parseInstant(file.path("modifiedTime").asText(file.path("createdTime").asText(null))),
                file.path("webViewLink").asText(resource.getResourceUrl()), file.toString());
    }

    private void collectDriveActivity(IntegrationResource resource, String fileId, String token) {
        String pageToken = null;
        do {
            JsonNode body = post(DRIVE_ACTIVITY_API + "/activity:query", token,
                    pageToken == null
                            ? Map.of("itemName", "items/" + fileId, "pageSize", 100)
                            : Map.of("itemName", "items/" + fileId, "pageSize", 100, "pageToken", pageToken));
            for (JsonNode activity : body.path("activities")) {
                JsonNode actor = activity.path("actors").isArray() && !activity.path("actors").isEmpty()
                        ? activity.path("actors").get(0)
                        : null;
                JsonNode knownUser = actor == null ? null : actor.path("user").path("knownUser");
                activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY,
                        "drive-activity:" + Integer.toUnsignedString(activity.toString().hashCode()),
                        knownUser == null ? null : knownUser.path("personName").asText(null), null, null,
                        parseInstant(activity.path("timestamp")
                                .asText(activity.path("timeRange").path("endTime").asText(null))),
                        resource.getResourceUrl(), activity.toString());
            }
            pageToken = body.path("nextPageToken").asText(null);
        } while (pageToken != null && !pageToken.isBlank());
    }

    private void collectComments(IntegrationResource resource, String fileId, String token) {
        String pageToken = null;
        do {
            String url = DRIVE_API + "/files/" + fileId
                    + "/comments?pageSize=100&fields=nextPageToken,comments(id,createdTime,modifiedTime,author,content,resolved,replies)"
                    + (pageToken == null ? "" : "&pageToken=" + pageToken);
            JsonNode body = get(url, token);
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
            pageToken = body.path("nextPageToken").asText(null);
        } while (pageToken != null && !pageToken.isBlank());
    }

    private void collectRevisions(IntegrationResource resource, String fileId, String token) {
        String pageToken = null;
        do {
            String url = DRIVE_API + "/files/" + fileId
                    + "/revisions?pageSize=100&fields=nextPageToken,revisions(id,modifiedTime,lastModifyingUser,originalFilename,mimeType)"
                    + (pageToken == null ? "" : "&pageToken=" + pageToken);
            JsonNode body = get(url, token);
            for (JsonNode revision : body.path("revisions")) {
                JsonNode author = revision.path("lastModifyingUser");
                activityStoreService.store(resource, IntegrationActivityType.GOOGLE_DRIVE_REVISION,
                        "revision:" + revision.path("id").asText(), author.path("permissionId").asText(null),
                        author.path("displayName").asText(null), author.path("emailAddress").asText(null),
                        parseInstant(revision.path("modifiedTime").asText(null)), resource.getResourceUrl(),
                        revision.toString());
            }
            pageToken = body.path("nextPageToken").asText(null);
        } while (pageToken != null && !pageToken.isBlank());
    }

    private JsonNode get(String uri, String token) {
        try {
            return restClient.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ProviderResourceAccessException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new ProviderResourceAccessException(503, exception);
        }
    }

    private JsonNode post(String uri, String token, Object request) {
        try {
            return restClient.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(request).retrieve().body(JsonNode.class);
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
