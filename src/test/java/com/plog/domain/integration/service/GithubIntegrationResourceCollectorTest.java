package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import com.plog.domain.integration.entity.CollectionPhase;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.project.entity.Project;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
    private String branchRefsBody = """
            [{"ref":"refs/heads/main","object":{"sha":"main-tip"}}]
            """;
    private String tagRefsBody = "[]";
    private final Map<String, String> commitsByRefSha = new HashMap<>();
    private final Map<String, String> tagObjectsBySha = new HashMap<>();
    private String issuesBody = "[]";
    private String pullsBody = "[]";
    private String pagedCommitRefSha;

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
                githubAppClient, activityStoreService,
                new GithubApiRateLimiter(properties()), builder.build());
    }

    @Test
    @DisplayName("프로젝트 시작일과 lastCollectedAt이 오래되어도 since를 보내지 않는다")
    void doesNotSendSinceForOldProjectDateOrWatermark() {
        Instant watermark = Instant.parse("2026-08-05T13:00:00Z");

        collect(resource(watermark), CollectionContext.noop());

        assertThat(requestedUris).noneMatch(uri -> uri.contains("since="));
    }

    @Test
    @DisplayName("브랜치와 태그 ref를 모두 조회하고 중복 commit SHA는 한 번만 저장한다")
    void collectsCommitsFromAllRefsAndStoresDuplicateShaOnce() {
        branchRefsBody = """
                [{"ref":"refs/heads/main","object":{"sha":"branch-tip"}},
                 {"ref":"refs/heads/release","object":{"sha":"release-tip"}}]
                """;
        tagRefsBody = """
                [{"ref":"refs/tags/v1.0.0","object":{"sha":"tag-tip"}}]
                """;
        commitsByRefSha.put("branch-tip", """
                [{"sha":"duplicate","author":{"id":"1","login":"chan"},
                  "commit":{"author":{"email":"chan@example.com","date":"2026-01-02T00:00:00Z"}},
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/commit/duplicate"}]
                """);
        commitsByRefSha.put("release-tip", """
                [{"sha":"duplicate","author":{"id":"1","login":"chan"},
                  "commit":{"author":{"email":"chan@example.com","date":"2026-01-02T00:00:00Z"}},
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/commit/duplicate"}]
                """);
        commitsByRefSha.put("tag-tip", """
                [{"sha":"tag-commit","author":{"id":"2","login":"min"},
                  "commit":{"author":{"email":"min@example.com","date":"2026-01-03T00:00:00Z"}},
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/commit/tag-commit"}]
                """);
        IntegrationResource resource = resource(null);

        collect(resource, CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/git/matching-refs/heads"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/git/matching-refs/tags"));
        assertThat(requestedUris)
                .filteredOn(uri -> uri.contains("/git/matching-refs/"))
                .allMatch(uri -> !uri.contains("page=") && !uri.contains("per_page="));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/commits?") && uri.contains("sha=branch-tip"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/commits?") && uri.contains("sha=release-tip"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/commits?") && uri.contains("sha=tag-tip"));
        verify(activityStoreService, times(1)).store(eq(resource), eq(IntegrationActivityType.GITHUB_COMMIT),
                eq("commit:duplicate"), any(), any(), any(), any(), any(), any());
        verify(activityStoreService, times(1)).store(eq(resource), eq(IntegrationActivityType.GITHUB_COMMIT),
                eq("commit:tag-commit"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("annotated tag는 commit 대상까지 해석해 전체 이력을 수집한다")
    void resolvesAnnotatedTagToCommitBeforeCollection() {
        branchRefsBody = "[]";
        tagRefsBody = """
                [{"ref":"refs/tags/v1.0.0","object":{"type":"tag","sha":"annotated-tag"}}]
                """;
        tagObjectsBySha.put("annotated-tag", """
                {"object":{"type":"commit","sha":"tagged-commit"}}
                """);
        commitsByRefSha.put("tagged-commit", """
                [{"sha":"tagged-commit","author":{"id":"2","login":"min"},
                  "commit":{"author":{"email":"min@example.com","date":"2026-01-03T00:00:00Z"}},
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/commit/tagged-commit"}]
                """);

        collect(resource(null), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/git/tags/annotated-tag"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/commits?") && uri.contains("sha=tagged-commit"));
        verify(activityStoreService).store(any(), eq(IntegrationActivityType.GITHUB_COMMIT),
                eq("commit:tagged-commit"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("annotated tag가 5단계를 넘어도 loop 없이 commit 대상까지 해석한다")
    void resolvesDeepAnnotatedTagChainWithoutFixedDepthLimit() {
        branchRefsBody = "[]";
        tagRefsBody = """
                [{"ref":"refs/tags/deep","object":{"type":"tag","sha":"tag-1"}}]
                """;
        for (int depth = 1; depth <= 6; depth++) {
            tagObjectsBySha.put("tag-" + depth, """
                    {"object":{"type":"tag","sha":"tag-%d"}}
                    """.formatted(depth + 1));
        }
        tagObjectsBySha.put("tag-7", """
                {"object":{"type":"commit","sha":"deep-tagged-commit"}}
                """);
        commitsByRefSha.put("deep-tagged-commit", """
                [{"sha":"deep-tagged-commit","author":{"id":"2","login":"min"},
                  "commit":{"author":{"email":"min@example.com","date":"2026-01-03T00:00:00Z"}},
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/commit/deep-tagged-commit"}]
                """);

        collect(resource(null), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/git/tags/tag-7"));
        assertThat(requestedUris)
                .anyMatch(uri -> uri.contains("/commits?") && uri.contains("sha=deep-tagged-commit"));
        verify(activityStoreService).store(any(), eq(IntegrationActivityType.GITHUB_COMMIT),
                eq("commit:deep-tagged-commit"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("commit 페이지가 101페이지를 넘어도 Link next를 따라 끝까지 저장한다")
    void collectsCommitHistoryPastOneHundredOnePages() {
        branchRefsBody = """
                [{"ref":"refs/heads/main","object":{"sha":"long-history"}}]
                """;
        pagedCommitRefSha = "long-history";

        collect(resource(null), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/commits?") && uri.contains("page=101"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/commits?") && uri.contains("page=102"));
        verify(activityStoreService, times(102)).store(any(), eq(IntegrationActivityType.GITHUB_COMMIT),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("comments가 0인 이슈는 comments API를 호출하지 않는다")
    void skipsCommentsCallWhenIssueHasNone() {
        issuesBody = """
                [{"number":7,"comments":0,"updated_at":"2026-08-05T12:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-08-01T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/issues/7"}]
                """;

        collect(resource(null), CollectionContext.noop());

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

        collect(resource(null), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/issues/7/comments"));
    }

    @Test
    @DisplayName("이슈와 이슈 댓글 요청에 since를 보내지 않는다")
    void requestsIssuesAndCommentsWithoutSince() {
        issuesBody = """
                [{"number":7,"comments":3,"updated_at":"2026-01-02T00:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-01-02T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/issues/7"}]
                """;

        collect(resource(Instant.parse("2026-08-05T13:00:00Z")), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/issues?"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/issues/7/comments"));
        assertThat(requestedUris.stream()
                .filter(uri -> uri.contains("/issues?") || uri.contains("/issues/7/comments")))
                .noneMatch(uri -> uri.contains("since="));
    }

    @Test
    @DisplayName("워터마크보다 오래된 PR도 reviews API를 호출한다")
    void collectsReviewsForOldPullRequest() {
        pullsBody = """
                [{"number":3,"updated_at":"2026-01-02T00:00:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-01-02T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/pull/3"},
                 {"number":9,"updated_at":"2026-08-05T12:30:00Z",
                  "user":{"id":"1","login":"chan"},"created_at":"2026-08-01T00:00:00Z",
                  "html_url":"https://github.com/UMC-Plog/Plog-FE/pull/9"}]
                """;

        collect(resource(Instant.parse("2026-08-05T13:00:00Z")), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/pulls/3/reviews"));
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

        collect(resource(null), CollectionContext.noop());

        assertThat(requestedUris).anyMatch(uri -> uri.contains("/pulls/3/reviews"));
    }

    @Test
    @DisplayName("이슈와 PR 목록을 created 오름차순으로 요청한다")
    void requestsStableOrderingForResume() {
        collect(resource(null), CollectionContext.noop());

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

        collect(resource(null), context);

        assertThat(requestedUris).noneMatch(uri -> uri.contains("/commits"));
        assertThat(requestedUris).noneMatch(uri -> uri.contains("/pulls?"));
        assertThat(requestedUris).anyMatch(uri -> uri.contains("/issues?"));
    }

    @Test
    @DisplayName("다른 리소스의 커서는 무시하고 처음부터 수집한다")
    void ignoresCursorFromAnotherResource() {
        RecordingContext context = new RecordingContext(
                new CollectionCursor(999L, CollectionPhase.ISSUES, null));

        collect(resource(null), context);

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

        collect(resource(null), context);

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

        collect(resource(null), context);

        assertThat(context.advances).containsExactly("PULL_REQUESTS:3", "ISSUES:7");
    }

    @Test
    @DisplayName("provider 호출마다 heartbeat를 남겨 잡 회수를 막는다")
    void reportsHeartbeatPerApiCall() {
        RecordingContext context = new RecordingContext(CollectionCursor.start());

        collect(resource(null), context);

        assertThat(context.heartbeats).isEqualTo(requestedUris.size());
    }

    private void collect(IntegrationResource resource, CollectionContext context) {
        collector.collect(resource, resource.getProjectIntegration(), context);
    }

    private void record(ClientHttpRequest request) {
        requestedUris.add(request.getURI().toString());
    }

    private ClientHttpResponse respond(ClientHttpRequest request) throws IOException {
        String uri = request.getURI().toString();
        String body = "[]";
        if (uri.contains("/git/matching-refs/heads")) {
            body = branchRefsBody;
        } else if (uri.contains("/git/matching-refs/tags")) {
            body = tagRefsBody;
        } else if (uri.contains("/git/tags/")) {
            body = tagObjectsBySha.getOrDefault(pathTail(uri), "{}");
        } else if (uri.contains("/commits?")) {
            String refSha = shaQueryValue(uri);
            if (refSha.equals(pagedCommitRefSha)) {
                int page = pageQueryValue(uri);
                body = """
                        [{"sha":"sha-%d","author":{"id":"1","login":"chan"},
                          "commit":{"author":{"email":"chan@example.com","date":"2026-01-02T00:00:00Z"}},
                          "html_url":"https://github.com/UMC-Plog/Plog-FE/commit/sha-%d"}]
                        """.formatted(page, page);
                var response = MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON);
                if (page < 102) {
                    response.header(HttpHeaders.LINK,
                            "<https://api.github.com/repos/UMC-Plog/Plog-FE/commits?per_page=100&sha="
                                    + refSha + "&page=" + (page + 1) + ">; rel=\"next\"");
                }
                return response.createResponse(request);
            }
            body = commitsByRefSha.getOrDefault(refSha, "[]");
        } else if (uri.contains("/issues?")) {
            body = issuesBody;
        } else if (uri.contains("/pulls?")) {
            body = pullsBody;
        }
        return MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON)
                .createResponse(request);
    }

    private String shaQueryValue(String uri) {
        String marker = "sha=";
        int start = uri.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int end = uri.indexOf('&', start);
        return end < 0 ? uri.substring(start + marker.length()) : uri.substring(start + marker.length(), end);
    }

    private int pageQueryValue(String uri) {
        String query = java.net.URI.create(uri).getRawQuery();
        if (query == null || query.isBlank()) {
            return 1;
        }
        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length == 2 && "page".equals(pair[0])) {
                return Integer.parseInt(pair[1]);
            }
        }
        return 1;
    }

    private String pathTail(String uri) {
        int start = uri.lastIndexOf('/');
        return start < 0 ? uri : uri.substring(start + 1);
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
        private int heartbeats;

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

        @Override
        public void heartbeat() {
            heartbeats++;
        }
    }
}
