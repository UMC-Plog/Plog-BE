package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import com.plog.domain.integration.entity.CollectionPhase;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.project.entity.Project;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

class GithubIntegrationResourceCollectorTest {

    private static final String REPOSITORY_URL = "https://github.com/UMC-Plog/Plog-FE";
    private static final LocalDate START_DAY = LocalDate.of(2026, 1, 1);

    private final List<String> requestedUris = new ArrayList<>();
    private final GithubAppClient githubAppClient = mock(GithubAppClient.class);
    private final IntegrationActivityStoreService activityStoreService =
            mock(IntegrationActivityStoreService.class);

    /** URI별 응답 본문. 테스트마다 갈아끼운다. */
    private String issuesBody = "[]";
    private String pullsBody = "[]";

    private GithubIntegrationResourceCollector collector;

    @BeforeEach
    void setUp() {
        given(githubAppClient.createInstallationAccessToken("150699151")).willReturn("token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder)
                .ignoreExpectOrder(true)
                .build();
        server.expect(ExpectedCount.manyTimes(), this::record).andRespond(this::respond);
        collector = new GithubIntegrationResourceCollector(
                githubAppClient, activityStoreService, properties(),
                new GithubApiRateLimiter(properties()), builder.build());
    }

    @Test
    @DisplayName("lastCollectedAt이 없으면 프로젝트 시작일을 since로 쓴다")
    void usesProjectStartDayOnFirstCollection() {
        collector.collect(resource(null), CollectionContext.noop());

        assertThat(commitsUri()).contains("since=2026-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("lastCollectedAt이 있으면 overlap을 뺀 시각을 since로 쓴다")
    void usesWatermarkMinusOverlapOnRecollection() {
        Instant watermark = Instant.parse("2026-08-05T13:00:00Z");

        collector.collect(resource(watermark), CollectionContext.noop());

        assertThat(commitsUri()).contains("since=2026-08-05T12:00:00Z");
    }

    @Test
    @DisplayName("comments가 0인 이슈는 comments API를 호출하지 않는다")
    void skipsCommentsCallWhenIssueHasNone() {
        issuesBody = """
                [{"number":7,"comments":0,"updated_at":"2026-08-05T12:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-08-01T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/issues/7"}]
                """;

        collector.collect(resource(null), CollectionContext.noop());

        assertThat(requestedUris).noneMatch(uri -> uri.contains("/issues/7/comments"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/issues/7/events"));
    }

    @Test
    @DisplayName("comments가 있는 이슈는 comments API를 호출한다")
    void callsCommentsWhenIssueHasThem() {
        issuesBody = """
                [{"number":7,"comments":3,"updated_at":"2026-08-05T12:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-08-01T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/issues/7"}]
                """;

        collector.collect(resource(null), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/issues/7/comments"));
    }

    @Test
    @DisplayName("워터마크보다 오래된 PR은 reviews API를 호출하지 않는다")
    void skipsReviewsCallForUnchangedPullRequest() {
        pullsBody = """
                [{"number":3,"updated_at":"2026-01-02T00:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-01-02T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/pull/3"},
                 {"number":9,"updated_at":"2026-08-05T12:30:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-08-01T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/pull/9"}]
                """;

        collector.collect(resource(Instant.parse("2026-08-05T13:00:00Z")), CollectionContext.noop());

        assertThat(requestedUris).noneMatch(uri -> uri.contains("/pulls/3/reviews"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/pulls/9/reviews"));
    }

    @Test
    @DisplayName("첫 수집이면 오래된 PR도 reviews를 호출한다")
    void collectsAllReviewsOnFirstCollection() {
        pullsBody = """
                [{"number":3,"updated_at":"2026-01-02T00:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-01-02T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/pull/3"}]
                """;

        collector.collect(resource(null), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/pulls/3/reviews"));
    }

    @Test
    @DisplayName("이슈와 PR 목록을 created 오름차순으로 요청한다")
    void requestsStableOrderingForResume() {
        collector.collect(resource(null), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(
                uri -> uri.contains("/issues?") && uri.contains("sort=created&direction=asc"));
        assertThat(requestedUris).anyMatch(
                uri -> uri.contains("/pulls?") && uri.contains("sort=created&direction=asc"));
    }

    @Test
    @DisplayName("커서가 ISSUES를 가리키면 commits와 pulls를 건너뛴다")
    void resumesFromIssuesPhase() {
        RecordingContext context = new RecordingContext(
                new CollectionCursor(1L, CollectionPhase.ISSUES, null));

        collector.collect(resource(null), context);

        assertThat(requestedUris).noneMatch(uri -> uri.contains("/commits"));
        assertThat(requestedUris).noneMatch(uri -> uri.contains("/pulls?"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/issues?"));
    }

    @Test
    @DisplayName("다른 리소스의 커서는 무시하고 처음부터 수집한다")
    void ignoresCursorFromAnotherResource() {
        RecordingContext context = new RecordingContext(
                new CollectionCursor(999L, CollectionPhase.ISSUES, null));

        collector.collect(resource(null), context);

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/commits"));
    }

    @Test
    @DisplayName("커서의 항목 번호 이하인 이슈는 건너뛴다")
    void skipsAlreadyProcessedIssues() {
        issuesBody = """
                [{"number":42,"comments":0,"updated_at":"2026-08-05T12:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-08-01T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/issues/42"},
                 {"number":43,"comments":0,"updated_at":"2026-08-05T12:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-08-02T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/issues/43"}]
                """;
        RecordingContext context = new RecordingContext(
                new CollectionCursor(1L, CollectionPhase.ISSUES, 42));

        collector.collect(resource(null), context);

        assertThat(requestedUris).noneMatch(uri -> uri.contains("/issues/42/events"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/issues/43/events"));
    }

    @Test
    @DisplayName("항목을 끝낼 때마다 advance를 호출한다")
    void reportsProgressPerItem() {
        issuesBody = """
                [{"number":7,"comments":0,"updated_at":"2026-08-05T12:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-08-01T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/issues/7"}]
                """;
        pullsBody = """
                [{"number":3,"updated_at":"2026-08-05T12:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-08-01T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/pull/3"}]
                """;
        RecordingContext context = new RecordingContext(CollectionCursor.start());

        collector.collect(resource(null), context);

        assertThat(context.advances).containsExactly("PULL_REQUESTS:3", "ISSUES:7");
    }

    private String commitsUri() {
        return requestedUris.stream()
                .filter(uri -> uri.contains("/commits"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("commits API가 호출되지 않았다: " + requestedUris));
    }

    private void record(ClientHttpRequest request) {
        requestedUris.add(request.getURI().toString());
    }

    private ClientHttpResponse respond(ClientHttpRequest request) throws IOException {
        String uri = request.getURI().toString();
        String body = "[]";
        if (uri.contains("/issues?")) {
            body = issuesBody;
        } else if (uri.contains("/pulls?")) {
            body = pullsBody;
        }
        return MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON)
                .createResponse(request);
    }

    private IntegrationResource resource(Instant lastCollectedAt) {
        Project project = mock(Project.class);
        given(project.getStartDay()).willReturn(START_DAY);
        ProjectIntegration integration = mock(ProjectIntegration.class);
        given(integration.getProject()).willReturn(project);
        given(integration.getProviderConnectionId()).willReturn("150699151");
        IntegrationResource resource = mock(IntegrationResource.class);
        given(resource.getId()).willReturn(1L);
        given(resource.getProjectIntegration()).willReturn(integration);
        given(resource.getResourceUrl()).willReturn(REPOSITORY_URL);
        given(resource.getLastCollectedAt()).willReturn(lastCollectedAt);
        return resource;
    }

    private IntegrationCollectionProperties properties() {
        return new IntegrationCollectionProperties(
                5_000L, 5, Duration.ofMinutes(30), 5, 25, Duration.ofHours(1), 0L, 100);
    }

    /** 커서를 주입하고 advance 호출을 기록하는 테스트용 컨텍스트. */
    private static final class RecordingContext implements CollectionContext {

        private final CollectionCursor cursor;
        private final List<String> advances = new ArrayList<>();

        private RecordingContext(CollectionCursor cursor) {
            this.cursor = cursor;
        }

        @Override
        public CollectionCursor cursor() {
            return cursor;
        }

        @Override
        public void enterResource(Long resourceId) {
            // GitHub collector는 리소스 경계를 모른다. 호출되면 안 된다.
            throw new AssertionError("collector must not call enterResource");
        }

        @Override
        public void advance(CollectionPhase phase, int itemNumber) {
            advances.add(phase + ":" + itemNumber);
        }
    }
}
