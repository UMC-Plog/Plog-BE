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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

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
                IntegrationActivityType.GOOGLE_DRIVE_REVISION,
                IntegrationActivityType.GOOGLE_DOCUMENT_SUGGESTION
        );
        assertThat(storedKeys()).contains(
                "drive-file:google-file-1:2026-08-01T09:00:00Z",
                "revision:rev-1",
                "document-snapshot:google-file-1:doc-revision-1"
        );
        assertThat(storedLatestKeys()).contains("comment:comment-1", "comment-reply:reply-1");
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
    @DisplayName("Drive Activity actor를 People API 한 번의 배치 조회로 보강한다")
    void enrichesDriveActivityActorsWithBatchedPeopleLookup() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[%s,%s,%s]}
                """.formatted(
                personOnlyActivityJson("people/editor", "2026-08-01T10:00:00Z"),
                personOnlyActivityJson("people/editor", "2026-08-01T10:30:00Z"),
                personOnlyActivityJson("people/reviewer", "2026-08-01T11:00:00Z")));
        expectPeopleBatch(fixture.server, List.of("people/editor", "people/reviewer"), """
                {"responses":[
                  {"requestedResourceName":"people/reviewer","person":{
                    "resourceName":"people/reviewer",
                    "names":[
                      {"displayName":"Old Reviewer"},
                      {"metadata":{"primary":true},"displayName":"Reviewer"}
                    ]
                  }},
                  {"requestedResourceName":"people/editor","person":{
                    "resourceName":"people/editor",
                    "names":[{"metadata":{"primary":true},"displayName":"Editor"}],
                    "emailAddresses":[{"metadata":{"primary":true},"value":"editor@example.com"}]
                  }}
                ]}
                """);
        expectComments(fixture.server, "{}");
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(resource, resource.getProjectIntegration(), CollectionContext.noop());

        fixture.server.verify();
        assertThat(storedActorsFor(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY)).containsExactly(
                new StoredActor("people/editor", "Editor", "editor@example.com"),
                new StoredActor("people/editor", "Editor", "editor@example.com"),
                new StoredActor("people/reviewer", "Reviewer", null)
        );
        assertThat(fixture.requestedUris.stream()
                .filter(uri -> uri.startsWith("https://people.googleapis.com/v1/people:batchGet")))
                .singleElement()
                .satisfies(uri -> assertThat(uri).containsOnlyOnce("resourceNames=people/editor"));
        verify(activityStoreService, never()).backfillActorDisplayInfo(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Google 현재 사용자 actor는 Drive Activity와 댓글에서 연동 계정 ID로 정규화한다")
    void normalizesCurrentGoogleUserAcrossDriveActivityAndComments() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);
        ProjectIntegration verifiedIntegration = mock(ProjectIntegration.class);
        given(verifiedIntegration.getExternalAccountId()).willReturn("google-sub-123");
        given(verifiedIntegration.getExternalAccountName()).willReturn("self@example.com");

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[{"primaryActionDetail":{"edit":{}},"actors":[{"user":{"knownUser":{
                  "personName":"people/108281305932882777267","isCurrentUser":true}}}],
                 "timestamp":"2026-08-01T10:00:00Z"}]}
                """);
        expectComments(fixture.server, """
                {"comments":[{"id":"comment-self","createdTime":"2026-08-01T12:00:00Z",
                  "author":{"me":true,"permissionId":"comment-permission","displayName":"유상완"},
                  "content":"hello"}]}
                """);
        expectReplies(fixture.server, "comment-self", null, """
                {"replies":[{"id":"reply-self","createdTime":"2026-08-01T12:10:00Z",
                  "author":{"me":true,"displayName":"유상완"},"content":"reply"}]}
                """);
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(resource, verifiedIntegration, CollectionContext.noop());

        fixture.server.verify();
        assertThat(storedActorsFor(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY)).containsExactly(
                new StoredActor("google-account:google-sub-123", null, "self@example.com")
        );
        assertThat(storedLatestActorsFor(IntegrationActivityType.GOOGLE_DRIVE_COMMENT)).containsExactly(
                new StoredActor("google-account:google-sub-123", "유상완", "self@example.com"),
                new StoredActor("google-account:google-sub-123", "유상완", "self@example.com")
        );
    }

    @Test
    @DisplayName("연동 계정 ID가 없으면 현재 사용자 actor의 원본 provider ID를 보존한다")
    void preservesOriginalCurrentGoogleUserActorWhenExternalAccountIdIsMissing() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);
        ProjectIntegration verifiedIntegration = mock(ProjectIntegration.class);
        given(verifiedIntegration.getExternalAccountId()).willReturn(null);
        given(verifiedIntegration.getExternalAccountName()).willReturn("self@example.com");

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[{"primaryActionDetail":{"edit":{}},"actors":[{"user":{"knownUser":{
                  "personName":"people/108281305932882777267","displayName":"유상완","isCurrentUser":true}}}],
                 "timestamp":"2026-08-01T10:00:00Z"}]}
                """);
        expectComments(fixture.server, """
                {"comments":[{"id":"comment-self","createdTime":"2026-08-01T12:00:00Z",
                  "author":{"me":true,"permissionId":"comment-permission","displayName":"유상완"},
                  "content":"hello"}]}
                """);
        expectReplies(fixture.server, "comment-self", null, """
                {"replies":[{"id":"reply-self","createdTime":"2026-08-01T12:10:00Z",
                  "author":{"me":true,"permissionId":"reply-permission","displayName":"유상완"},"content":"reply"}]}
                """);
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(resource, verifiedIntegration, CollectionContext.noop());

        fixture.server.verify();
        assertThat(storedActorsFor(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY)).containsExactly(
                new StoredActor("people/108281305932882777267", "유상완", null)
        );
        assertThat(storedLatestActorsFor(IntegrationActivityType.GOOGLE_DRIVE_COMMENT)).containsExactly(
                new StoredActor("comment-permission", "유상완", null),
                new StoredActor("reply-permission", "유상완", null)
        );
    }

    @Test
    @DisplayName("같은 표시 이름이어도 현재 사용자가 아니면 연동 계정 ID로 정규화하지 않는다")
    void doesNotNormalizeNonCurrentGoogleUserByDisplayName() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);
        ProjectIntegration verifiedIntegration = mock(ProjectIntegration.class);
        given(verifiedIntegration.getExternalAccountId()).willReturn("google-sub-123");
        given(verifiedIntegration.getExternalAccountName()).willReturn("self@example.com");

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, "{}");
        expectComments(fixture.server, """
                {"comments":[{"id":"comment-other","createdTime":"2026-08-01T12:00:00Z",
                  "author":{"me":false,"displayName":"유상완"},"content":"other"}]}
                """);
        expectReplies(fixture.server, "comment-other", null, "{}");
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(resource, verifiedIntegration, CollectionContext.noop());

        fixture.server.verify();
        assertThat(storedLatestActorsFor(IntegrationActivityType.GOOGLE_DRIVE_COMMENT)).containsExactly(
                new StoredActor(null, "유상완", null)
        );
    }

    @Test
    @DisplayName("Drive Activity 페이지가 바뀌어도 이미 조회한 actor는 다시 조회하지 않는다")
    void cachesResolvedDriveActivityActorsAcrossPages() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[%s],"nextPageToken":"page-2"}
                """.formatted(personOnlyActivityJson("people/editor", "2026-08-01T10:00:00Z")));
        expectPeopleBatch(fixture.server, List.of("people/editor"), peopleResponse(
                "people/editor", "Editor", "editor@example.com"));
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100,"pageToken":"page-2"}
                """, """
                {"activities":[%s,%s]}
                """.formatted(
                personOnlyActivityJson("people/editor", "2026-08-01T10:30:00Z"),
                personOnlyActivityJson("people/reviewer", "2026-08-01T11:00:00Z")));
        expectPeopleBatch(fixture.server, List.of("people/reviewer"), peopleResponse(
                "people/reviewer", "Reviewer", "reviewer@example.com"));
        expectComments(fixture.server, "{}");
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(resource, resource.getProjectIntegration(), CollectionContext.noop());

        fixture.server.verify();
        assertThat(storedActorsFor(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY)).containsExactly(
                new StoredActor("people/editor", "Editor", "editor@example.com"),
                new StoredActor("people/editor", "Editor", "editor@example.com"),
                new StoredActor("people/reviewer", "Reviewer", "reviewer@example.com")
        );
        assertThat(fixture.requestedUris).filteredOn(
                uri -> uri.startsWith("https://people.googleapis.com/v1/people:batchGet"))
                .hasSize(2);
        verify(activityStoreService, never()).backfillActorDisplayInfo(any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"403, 424", "429, 429", "503, 503"})
    @DisplayName("People API 배치 실패는 downstream 수집 후 리소스 실패로 전달한다")
    void propagatesPeopleLookupProviderErrorAfterCollectingDownstream(
            int responseStatus,
            int collectionStatus
    ) {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[%s]}
                """.formatted(personOnlyActivityJson("people/editor", "2026-08-01T10:00:00Z")));
        fixture.server.expect(requestTo(Matchers.startsWith(
                        "https://people.googleapis.com/v1/people:batchGet")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatusCode.valueOf(responseStatus))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"people unavailable\"}}"));
        expectComments(fixture.server, "{}");
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        assertThatThrownBy(() -> fixture.collector.collect(
                resource, resource.getProjectIntegration(), CollectionContext.noop()))
                .isInstanceOfSatisfying(ProviderResourceAccessException.class,
                        exception -> assertThat(exception.statusCode()).isEqualTo(collectionStatus));

        fixture.server.verify();
        verifyNoActivityStored(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY);
        verify(activityStoreService, never()).backfillActorDisplayInfo(any(), any(), any(), any());
    }

    @Test
    @DisplayName("People API 네트워크 실패는 downstream 수집 후 일시 장애로 전달한다")
    void propagatesPeopleLookupNetworkFailureAfterCollectingDownstream() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[%s]}
                """.formatted(personOnlyActivityJson("people/editor", "2026-08-01T10:00:00Z")));
        fixture.server.expect(requestTo(Matchers.startsWith(
                        "https://people.googleapis.com/v1/people:batchGet")))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection reset");
                });
        expectComments(fixture.server, "{}");
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        assertThatThrownBy(() -> fixture.collector.collect(
                resource, resource.getProjectIntegration(), CollectionContext.noop()))
                .isInstanceOfSatisfying(ProviderResourceAccessException.class,
                        exception -> assertThat(exception.statusCode()).isEqualTo(503));

        fixture.server.verify();
        verifyNoActivityStored(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY);
        verify(activityStoreService, never()).backfillActorDisplayInfo(any(), any(), any(), any());
    }

    @Test
    @DisplayName("People 프로필에 이름·이메일이 없으면 해당 actor 활동만 제외하고 수집은 완료한다")
    void skipsActorActivityWhenPeopleProfileHasNoDisplayInformation() {
        Fixture fixture = fixture();
        IntegrationResource resource = resource(IntegrationResourceType.GOOGLE_DOCUMENT);

        expectFileMetadata(fixture.server);
        expectDriveActivity(fixture.server, """
                {"itemName":"items/google-file-1","pageSize":100}
                """, """
                {"activities":[%s]}
                """.formatted(personOnlyActivityJson("people/editor", "2026-08-01T10:00:00Z")));
        expectPeopleBatch(fixture.server, List.of("people/editor"), """
                {"responses":[{"requestedResourceName":"people/editor","person":{
                  "resourceName":"people/editor"}}]}
                """);
        expectComments(fixture.server, "{}");
        expectRevisions(fixture.server, "{}");
        expectDocumentSnapshot(fixture.server);

        fixture.collector.collect(resource, resource.getProjectIntegration(), CollectionContext.noop());

        fixture.server.verify();
        verifyNoActivityStored(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY);
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
        assertThat(storedLatestKeys()).contains(
                "comment:comment-deleted",
                "comment-reply:reply-1",
                "comment-reply:reply-deleted"
        );
        assertThat(storedLatestPayloadsFor(IntegrationActivityType.GOOGLE_DRIVE_COMMENT))
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

    private void expectPeopleBatch(
            MockRestServiceServer server,
            List<String> resourceNames,
            String responseBody
    ) {
        server.expect(requestTo(Matchers.startsWith("https://people.googleapis.com/v1/people:batchGet")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(queryParam("personFields", "names,emailAddresses"))
                .andExpect(queryParam("sources", "READ_SOURCE_TYPE_PROFILE"))
                .andExpect(queryParam("resourceNames", resourceNames.toArray(String[]::new)))
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

    private String personOnlyActivityJson(String personName, String timestamp) {
        return """
                {"primaryActionDetail":{"edit":{}},"actors":[{"user":{"knownUser":{
                  "personName":"%s"}}}],"timestamp":"%s"}
                """.formatted(personName, timestamp);
    }

    private String peopleResponse(String resourceName, String displayName, String email) {
        return """
                {"responses":[{"requestedResourceName":"%s","person":{
                  "resourceName":"%s",
                  "names":[{"metadata":{"primary":true},"displayName":"%s"}],
                  "emailAddresses":[{"metadata":{"primary":true},"value":"%s"}]
                }}]}
                """.formatted(resourceName, resourceName, displayName, email);
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

    private List<String> storedLatestKeys() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(activityStoreService, atLeastOnce()).storeLatestProviderPayload(
                any(), any(), captor.capture(), any(), any(), any(), any(), any(), any());
        return captor.getAllValues();
    }

    private List<String> storedLatestPayloadsFor(IntegrationActivityType activityType) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(activityStoreService, atLeastOnce()).storeLatestProviderPayload(
                any(), eq(activityType), any(), any(), any(), any(), any(), any(), captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private List<StoredActor> storedActorsFor(IntegrationActivityType activityType) {
        ArgumentCaptor<String> providerIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> loginCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(activityStoreService, atLeastOnce()).store(
                any(), eq(activityType), any(), providerIdCaptor.capture(), loginCaptor.capture(),
                emailCaptor.capture(), any(), any(), any());
        return storedActors(providerIdCaptor, loginCaptor, emailCaptor);
    }

    private List<StoredActor> storedLatestActorsFor(IntegrationActivityType activityType) {
        ArgumentCaptor<String> providerIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> loginCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(activityStoreService, atLeastOnce()).storeLatestProviderPayload(
                any(), eq(activityType), any(), providerIdCaptor.capture(), loginCaptor.capture(),
                emailCaptor.capture(), any(), any(), any());
        return storedActors(providerIdCaptor, loginCaptor, emailCaptor);
    }

    private List<StoredActor> storedActors(
            ArgumentCaptor<String> providerIdCaptor,
            ArgumentCaptor<String> loginCaptor,
            ArgumentCaptor<String> emailCaptor
    ) {
        List<StoredActor> actors = new ArrayList<>();
        for (int index = 0; index < providerIdCaptor.getAllValues().size(); index++) {
            actors.add(new StoredActor(
                    providerIdCaptor.getAllValues().get(index),
                    loginCaptor.getAllValues().get(index),
                    emailCaptor.getAllValues().get(index)
            ));
        }
        return actors;
    }

    private void verifyNoActivityStored(IntegrationActivityType activityType) {
        verify(activityStoreService, never()).store(
                any(), eq(activityType), any(), any(), any(), any(), any(), any(), any());
    }

    private record Fixture(
            MockRestServiceServer server,
            GoogleIntegrationResourceCollector collector,
            List<String> requestedUris
    ) {
    }

    private record StoredActor(String providerId, String login, String email) {
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
