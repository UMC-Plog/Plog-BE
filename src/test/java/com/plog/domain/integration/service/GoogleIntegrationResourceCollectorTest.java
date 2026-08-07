package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.ProjectIntegration;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleIntegrationResourceCollectorTest {

    private static final String TOKEN = "access-token";
    private static final String FILE_ID = "google-file-1";
    private static final String RESOURCE_URL = "https://docs.google.com/document/d/google-file-1/edit";

    private final ProjectIntegrationService projectIntegrationService = mock(ProjectIntegrationService.class);
    private final IntegrationActivityStoreService activityStoreService =
            mock(IntegrationActivityStoreService.class);

    @Test
    @DisplayName("Spring 컨텍스트는 운영 생성자로 Google collector 빈을 생성한다")
    void createsCollectorThroughProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(ProjectIntegrationService.class, () -> projectIntegrationService)
                .withBean(IntegrationActivityStoreService.class, () -> activityStoreService)
                .withUserConfiguration(GoogleIntegrationResourceCollector.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GoogleIntegrationResourceCollector.class);
                });
    }

    @Test
    @DisplayName("Docs 수집은 Drive Activity 첫/다음 페이지를 JSON body로 보내고 downstream까지 저장한다")
    void collectsDocsResourceWithJsonDriveActivityPaginationAndDownstreamApis() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[%s],"nextPageToken":"page-2"}
                """.formatted(activityJson("2026-08-01T10:00:00Z")));
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100,"pageToken":"page-2"}
                """, """
                {"activities":[%s]}
                """.formatted(activityJson("2026-08-01T11:00:00Z")));
        expectComments(fixture.server, """
                {"comments":[{"id":"comment-1","createdTime":"2026-08-01T12:00:00Z",
                  "author":{"permissionId":"commenter-1","displayName":"Commenter","emailAddress":"c@example.com"},
                  "content":"hello"}]}
                """);
        expectReplies(fixture.server, "comment-1", null, """
                {"replies":[{"id":"reply-1","createdTime":"2026-08-01T12:30:00Z",
                  "author":{"permissionId":"replyer-1","displayName":"Replyer","emailAddress":"r@example.com"},
                  "content":"reply"}]}
                """);
        expectRevisions(fixture.server, """
                {"revisions":[{"id":"rev-1","modifiedTime":"2026-08-01T13:00:00Z",
                  "lastModifyingUser":{"permissionId":"editor-2","displayName":"Editor2","emailAddress":"e2@example.com"}}]}
                """);
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(
                resource, resource.getProjectIntegration(), CollectionContext.noop());

        fixture.server.verify();
        assertThat(storedTypes()).containsExactly(
                IntegrationActivityType.GOOGLE_DRIVE_FILE_SNAPSHOT,
                IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY,
                IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY,
                IntegrationActivityType.GOOGLE_DRIVE_COMMENT,
                IntegrationActivityType.GOOGLE_DRIVE_COMMENT,
                IntegrationActivityType.GOOGLE_DRIVE_REVISION,
                IntegrationActivityType.GOOGLE_DOCUMENT_SUGGESTION
        );
        assertThat(storedKeys()).contains(
                "drive-file:google-file-1:2026-08-01T09:00:00Z",
                "comment:comment-1",
                "comment-reply:reply-1",
                "revision:rev-1",
                "document-snapshot:google-file-1:doc-revision-1"
        );
    }

    @Test
    @DisplayName("Slides 리소스는 공통 Drive 수집 뒤 presentation snapshot까지 저장한다")
    void collectsSlidesResourceThroughPresentationSnapshot() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_PRESENTATION);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[]}
                """);
        expectComments(fixture.server, "{}");
        expectRevisions(fixture.server, "{}");
        expectPresentationSnapshot(fixture.server);

        fixture.collector.collect(
                resource, resource.getProjectIntegration(), CollectionContext.noop());

        fixture.server.verify();
        assertThat(storedTypes()).containsExactly(
                IntegrationActivityType.GOOGLE_DRIVE_FILE_SNAPSHOT,
                IntegrationActivityType.GOOGLE_PRESENTATION_SNAPSHOT
        );
        assertThat(storedKeys()).anyMatch(key -> key.startsWith("presentation-snapshot:google-file-1:"));
    }

    @Test
    @DisplayName("Google provider 호출마다 heartbeat를 남기고 pagination 요청도 포함한다")
    void reportsHeartbeatPerProviderRequestAcrossPagination() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);
        RecordingContext context = new RecordingContext();

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[],"nextPageToken":"page-2"}
                """);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100,"pageToken":"page-2"}
                """, """
                {"activities":[]}
                """);
        expectComments(fixture.server, "{}");
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(resource, resource.getProjectIntegration(), context);

        fixture.server.verify();
        assertThat(fixture.requestedUris).hasSize(6);
        assertThat(context.heartbeats).isEqualTo(fixture.requestedUris.size());
    }

    @Test
    @DisplayName("검증에서 반환된 최신 연동의 access token을 사용한다")
    void usesAccessTokenFromVerifiedIntegration() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);
        ProjectIntegration staleIntegration = resource.getProjectIntegration();
        ProjectIntegration verifiedIntegration = mock(ProjectIntegration.class);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[]}
                """);
        expectComments(fixture.server, "{}");
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(resource, verifiedIntegration, CollectionContext.noop());

        fixture.server.verify();
        verify(projectIntegrationService).decryptAccessToken(verifiedIntegration);
        verify(projectIntegrationService, never()).decryptAccessToken(staleIntegration);
    }

    @Test
    @DisplayName("Drive Activity 400은 실패로 전파하고 이후 optional API를 호출하지 않는다")
    void propagatesDriveActivityBadRequestAndStopsDownstreamCalls() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        fixture.server.expect(requestTo("https://driveactivity.googleapis.com/v2/activity:query"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid JSON payload\"}}"));

        assertThatThrownBy(() -> fixture.collector.collect(
                resource, resource.getProjectIntegration(), CollectionContext.noop()))
                .isInstanceOf(ProviderResourceAccessException.class)
                .extracting("statusCode")
                .isEqualTo(400);

        fixture.server.verify();
        verify(activityStoreService, atLeastOnce()).store(
                eq(resource), eq(IntegrationActivityType.GOOGLE_DRIVE_FILE_SNAPSHOT),
                any(), any(), any(), any(), any(), any(), any());
        verify(activityStoreService, never()).store(
                eq(resource), eq(IntegrationActivityType.GOOGLE_DRIVE_COMMENT),
                any(), any(), any(), any(), any(), any(), any());
        verify(activityStoreService, never()).store(
                eq(resource), eq(IntegrationActivityType.GOOGLE_DOCUMENT_SUGGESTION),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("optional 하위 API의 404만 무시하고 다음 수집 단계로 진행한다")
    void ignoresOptionalNotFoundAndContinuesToNextSteps() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        fixture.server.expect(requestTo("https://driveactivity.googleapis.com/v2/activity:query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withResourceNotFound());
        expectComments(fixture.server, "{}");
        fixture.server.expect(requestTo(Matchers.containsString("/files/" + FILE_ID + "/revisions")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(
                resource, resource.getProjectIntegration(), CollectionContext.noop());

        fixture.server.verify();
        assertThat(storedTypes()).containsExactly(
                IntegrationActivityType.GOOGLE_DRIVE_FILE_SNAPSHOT,
                IntegrationActivityType.GOOGLE_DOCUMENT_SUGGESTION
        );
    }

    @Test
    @DisplayName("Drive Activity providerEventKey는 JSON 필드 순서가 달라도 안정적이다")
    void createsDeterministicDriveActivityEventKeys() {
        String firstKey = collectSingleActivityKey(activityJson("2026-08-01T10:00:00Z"));
        String sameActivityWithDifferentFieldOrder = """
                {"timestamp":"2026-08-01T10:00:00Z","actors":[{"user":{"knownUser":{
                  "emailAddress":"editor@example.com","displayName":"Editor","personName":"people/editor"}}}],
                 "primaryActionDetail":{"edit":{}}}
                """;
        String secondKey = collectSingleActivityKey(sameActivityWithDifferentFieldOrder);

        assertThat(secondKey).isEqualTo(firstKey);
    }

    @Test
    @DisplayName("삭제된 Google 댓글과 답글을 includeDeleted로 페이지네이션하며 저장한다")
    void collectsDeletedCommentsAndPaginatedDeletedReplies() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[]}
                """);
        expectComments(fixture.server, """
                {"comments":[{"id":"comment-deleted","createdTime":"2026-08-01T12:00:00Z",
                  "author":{"permissionId":"commenter-1","displayName":"Commenter","emailAddress":"c@example.com"},
                  "content":"deleted comment","deleted":true}]}
                """);
        expectReplies(fixture.server, "comment-deleted", null, """
                {"replies":[{"id":"reply-1","createdTime":"2026-08-01T12:30:00Z",
                  "author":{"permissionId":"replyer-1","displayName":"Replyer","emailAddress":"r@example.com"},
                  "content":"first page"}],"nextPageToken":"reply-page-2"}
                """);
        expectReplies(fixture.server, "comment-deleted", "reply-page-2", """
                {"replies":[{"id":"reply-deleted","createdTime":"2026-08-01T12:45:00Z",
                  "author":{"permissionId":"replyer-2","displayName":"Replyer2","emailAddress":"r2@example.com"},
                  "content":"deleted reply","deleted":true}]}
                """);
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(
                resource, resource.getProjectIntegration(), CollectionContext.noop());

        fixture.server.verify();
        assertThat(storedKeys()).contains(
                "comment:comment-deleted",
                "comment-reply:reply-1",
                "comment-reply:reply-deleted"
        );
        assertThat(storedPayloadsFor(IntegrationActivityType.GOOGLE_DRIVE_COMMENT))
                .anySatisfy(payload -> assertThat(payload).contains("\"id\":\"comment-deleted\"", "\"deleted\":true"))
                .anySatisfy(payload -> assertThat(payload).contains("\"id\":\"reply-deleted\"", "\"deleted\":true"));
    }

    private String collectSingleActivityKey(String activityJson) {
        clearInvocations(activityStoreService);
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[%s]}
                """.formatted(activityJson));
        fixture.server.expect(requestTo(Matchers.containsString("/files/" + FILE_ID + "/comments")))
                .andRespond(withResourceNotFound());
        fixture.server.expect(requestTo(Matchers.containsString("/files/" + FILE_ID + "/revisions")))
                .andRespond(withResourceNotFound());
        fixture.server.expect(requestTo(Matchers.containsString("/documents/" + FILE_ID)))
                .andRespond(withResourceNotFound());

        fixture.collector.collect(
                resource, resource.getProjectIntegration(), CollectionContext.noop());
        fixture.server.verify();

        return storedKeysFor(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY).get(0);
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        List<String> requestedUris = new ArrayList<>();
        builder.requestInterceptor((request, body, execution) -> {
            requestedUris.add(request.getURI().toString());
            return execution.execute(request, body);
        });
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        given(projectIntegrationService.decryptAccessToken(any(ProjectIntegration.class))).willReturn(TOKEN);
        return new Fixture(
                server,
                new GoogleIntegrationResourceCollector(projectIntegrationService, activityStoreService, builder.build()),
                requestedUris
        );
    }

    private void expectFileMetadata(MockRestServiceServer server) {
        server.expect(requestTo(Matchers.containsString("https://www.googleapis.com/drive/v3/files/" + FILE_ID)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {"id":"google-file-1","name":"Spec","mimeType":"application/vnd.google-apps.document",
                         "createdTime":"2026-08-01T08:00:00Z","modifiedTime":"2026-08-01T09:00:00Z",
                         "lastModifyingUser":{"permissionId":"editor-1","displayName":"Editor","emailAddress":"editor@example.com"},
                         "webViewLink":"https://docs.google.com/document/d/google-file-1/edit"}
                        """, MediaType.APPLICATION_JSON));
    }

    private void expectDriveActivity(MockRestServiceServer server, String expectedBody, String responseBody) {
        server.expect(requestTo("https://driveactivity.googleapis.com/v2/activity:query"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expectedBody))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private void expectComments(MockRestServiceServer server, String responseBody) {
        server.expect(requestTo(Matchers.allOf(
                        Matchers.containsString("/files/" + FILE_ID + "/comments?"),
                        Matchers.containsString("pageSize=100"),
                        Matchers.containsString("includeDeleted=true"),
                        Matchers.containsString("fields=nextPageToken,comments("),
                        Matchers.containsString("deleted")
                )))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private void expectReplies(MockRestServiceServer server, String commentId, String pageToken, String responseBody) {
        server.expect(requestTo(Matchers.allOf(
                        Matchers.containsString("/files/" + FILE_ID + "/comments/" + commentId + "/replies?"),
                        Matchers.containsString("pageSize=100"),
                        Matchers.containsString("includeDeleted=true"),
                        Matchers.containsString("fields=nextPageToken,replies("),
                        Matchers.containsString("deleted"),
                        pageToken == null
                                ? Matchers.not(Matchers.containsString("pageToken="))
                                : Matchers.containsString("pageToken=" + pageToken)
                )))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private void expectRevisions(MockRestServiceServer server, String responseBody) {
        server.expect(requestTo(Matchers.containsString("/files/" + FILE_ID + "/revisions")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    private void expectDocumentSnapshot(MockRestServiceServer server) {
        server.expect(requestTo(Matchers.containsString("https://docs.googleapis.com/v1/documents/" + FILE_ID)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"documentId":"google-file-1","revisionId":"doc-revision-1","title":"Spec"}
                        """, MediaType.APPLICATION_JSON));
    }

    private void expectPresentationSnapshot(MockRestServiceServer server) {
        server.expect(requestTo(Matchers.containsString("https://slides.googleapis.com/v1/presentations/" + FILE_ID)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"presentationId":"google-file-1","revisionId":"slide-revision-1","title":"Deck"}
                        """, MediaType.APPLICATION_JSON));
    }

    private IntegrationResource resource(IntegrationResourceType resourceType) {
        ProjectIntegration integration = mock(ProjectIntegration.class);
        IntegrationResource resource = mock(IntegrationResource.class);
        given(resource.getId()).willReturn(119L);
        given(resource.getProjectIntegration()).willReturn(integration);
        given(resource.getProviderResourceId()).willReturn(FILE_ID);
        given(resource.getResourceUrl()).willReturn(RESOURCE_URL);
        given(resource.getResourceType()).willReturn(resourceType);
        return resource;
    }

    private String activityJson(String timestamp) {
        return """
                {"primaryActionDetail":{"edit":{}},"actors":[{"user":{"knownUser":{
                  "personName":"people/editor","displayName":"Editor","emailAddress":"editor@example.com"}}}],
                 "timestamp":"%s"}
                """.formatted(timestamp);
    }

    private List<IntegrationActivityType> storedTypes() {
        ArgumentCaptor<IntegrationActivityType> captor = ArgumentCaptor.forClass(IntegrationActivityType.class);
        verify(activityStoreService, atLeastOnce()).store(
                any(), captor.capture(), any(), any(), any(), any(), any(), any(), any());
        return captor.getAllValues();
    }

    private List<String> storedKeys() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(activityStoreService, atLeastOnce()).store(
                any(), any(), captor.capture(), any(), any(), any(), any(), any(), any());
        return captor.getAllValues();
    }

    private List<String> storedKeysFor(IntegrationActivityType activityType) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(activityStoreService, atLeastOnce()).store(
                any(), eq(activityType), captor.capture(), any(), any(), any(), any(), any(), any());
        return new ArrayList<>(captor.getAllValues());
    }

    private List<String> storedPayloadsFor(IntegrationActivityType activityType) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(activityStoreService, atLeastOnce()).store(
                any(), eq(activityType), any(), any(), any(), any(), any(), any(), captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private record Fixture(
            MockRestServiceServer server,
            GoogleIntegrationResourceCollector collector,
            List<String> requestedUris
    ) {
    }

    private static final class RecordingContext implements CollectionContext {

        private int heartbeats;

        @Override
        public CollectionCursor cursor() {
            return CollectionCursor.start();
        }

        @Override
        public void enterResource(Long resourceId) {
            throw new AssertionError("collector must not call enterResource");
        }

        @Override
        public void advance(com.plog.domain.integration.entity.CollectionPhase phase, int itemNumber) {
            throw new AssertionError("collector must not call advance");
        }

        @Override
        public void heartbeat() {
            heartbeats++;
        }
    }
}
