package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.config.IntegrationCollectionProperties;
import com.plog.domain.integration.entity.CollectionPhase;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** GitHub App 설치 저장소에서 commit, PR, review, issue 및 issue 활동 원문을 수집한다. */
@Slf4j
@Component
class GithubIntegrationResourceCollector implements IntegrationResourceCollector {

    private static final String API_BASE_URL = "https://api.github.com";
    private static final int MAX_API_PAGE_COUNT = 100;

    private final GithubAppClient githubAppClient;
    private final IntegrationActivityStoreService activityStoreService;
    private final IntegrationCollectionProperties properties;
    private final GithubApiRateLimiter rateLimiter;
    private final RestClient restClient;

    /** 생성자가 둘이라 Spring이 주입 대상을 고를 수 없다. 이쪽이 운영용이다. */
    @Autowired
    GithubIntegrationResourceCollector(
            GithubAppClient githubAppClient,
            IntegrationActivityStoreService activityStoreService,
            IntegrationCollectionProperties properties,
            GithubApiRateLimiter rateLimiter
    ) {
        this(githubAppClient, activityStoreService, properties, rateLimiter,
                ProviderRestClientFactory.create());
    }

    /** 테스트에서 MockRestServiceServer를 물리기 위한 생성자다. */
    GithubIntegrationResourceCollector(
            GithubAppClient githubAppClient,
            IntegrationActivityStoreService activityStoreService,
            IntegrationCollectionProperties properties,
            GithubApiRateLimiter rateLimiter,
            RestClient restClient
    ) {
        this.githubAppClient = githubAppClient;
        this.activityStoreService = activityStoreService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.restClient = restClient;
    }

    @Override
    public List<LinkType> providers() {
        return List.of(LinkType.GITHUB);
    }

    @Override
    public void collect(IntegrationResource resource, CollectionContext context) {
        String accessToken = githubAppClient.createInstallationAccessToken(
                resource.getProjectIntegration().getProviderConnectionId());
        String repositoryPath = repositoryPath(resource.getResourceUrl());
        Instant watermark = resource.getLastCollectedAt();
        String since = sinceParameter(resource, watermark);

        // 재개 대상 리소스가 아니면 커서를 무시하고 처음부터 수집한다.
        CollectionCursor cursor = context.cursor().resumesResource(resource.getId())
                ? context.cursor()
                : CollectionCursor.start();

        if (!cursor.skipsPhase(CollectionPhase.COMMITS)) {
            collectCommits(resource, repositoryPath, since, accessToken, context);
        }
        if (!cursor.skipsPhase(CollectionPhase.PULL_REQUESTS)) {
            collectPullRequests(resource, repositoryPath, accessToken, watermark, cursor, context);
        }
        if (!cursor.skipsPhase(CollectionPhase.ISSUES)) {
            collectIssues(resource, repositoryPath, since, accessToken, cursor, context);
        }
    }

    /**
     * 재수집은 지난 수집 이후 변경분만 가져온다. overlap을 빼는 것은 시계 오차와 provider 반영
     * 지연을 흡수하기 위해서다 — 겹쳐 가져와도 활동 저장이 멱등이라 안전하다.
     */
    private String sinceParameter(IntegrationResource resource, Instant watermark) {
        Instant since = watermark == null
                ? resource.getProjectIntegration().getProject().getStartDay()
                        .atStartOfDay(ZoneOffset.UTC).toInstant()
                : collectionFloor(watermark);
        return since.toString();
    }

    /** 워터마크보다 오래된 항목은 지난 수집 이후 바뀐 것이 없다. 첫 수집이면 전부 대상이다. */
    private boolean isChangedSince(JsonNode node, Instant watermark) {
        if (watermark == null) {
            return true;
        }
        Instant updatedAt = parseInstant(node.path("updated_at").asText(null));
        return updatedAt == null || !updatedAt.isBefore(collectionFloor(watermark));
    }

    /** since 파라미터와 클라이언트 필터가 같은 기준선을 봐야 경계에서 항목이 새지 않는다. */
    private Instant collectionFloor(Instant watermark) {
        return watermark.minus(properties.watermarkOverlap());
    }

    private void collectCommits(IntegrationResource resource, String repositoryPath, String since,
            String token, CollectionContext context) {
        for (JsonNode commit : getPages("/repos/" + repositoryPath + "/commits?per_page=100&since=" + since, token, context)) {
            JsonNode author = commit.path("author");
            JsonNode authorCommit = commit.path("commit").path("author");
            activityStoreService.store(resource, IntegrationActivityType.GITHUB_COMMIT,
                    "commit:" + commit.path("sha").asText(), author.path("id").asText(null), author.path("login").asText(null),
                    authorCommit.path("email").asText(null), parseInstant(authorCommit.path("date").asText(null)),
                    commit.path("html_url").asText(resource.getResourceUrl()), commit.toString());
        }
    }

    private void collectPullRequests(
            IntegrationResource resource,
            String repositoryPath,
            String token,
            Instant watermark,
            CollectionCursor cursor,
            CollectionContext context
    ) {
        // /pulls는 since를 지원하지 않는다. created 오름차순으로 고정해 재개 지점을 안정시키고,
        // 변경 없는 PR은 reviews 호출만 건너뛴다. 리스트 페이지는 100건 단위라 비용이 작다.
        for (JsonNode pullRequest : getPages(
                "/repos/" + repositoryPath + "/pulls?state=all&per_page=100&sort=created&direction=asc", token, context)) {
            int pullRequestNumber = pullRequest.path("number").asInt();
            if (cursor.skipsItem(CollectionPhase.PULL_REQUESTS, pullRequestNumber)) {
                continue;
            }
            JsonNode author = pullRequest.path("user");
            String number = pullRequest.path("number").asText();
            activityStoreService.store(resource, IntegrationActivityType.GITHUB_PULL_REQUEST,
                    "pull-request:" + number, author.path("id").asText(null), author.path("login").asText(null), null,
                    parseInstant(pullRequest.path("created_at").asText(null)), pullRequest.path("html_url").asText(resource.getResourceUrl()),
                    pullRequest.toString());
            // 변경 없는 PR은 리뷰도 그대로다. 이미 저장된 것을 다시 가져올 이유가 없다.
            if (isChangedSince(pullRequest, watermark)) {
                for (JsonNode review : getPages("/repos/" + repositoryPath + "/pulls/" + number + "/reviews?per_page=100", token, context)) {
                    JsonNode reviewer = review.path("user");
                    activityStoreService.store(resource, IntegrationActivityType.GITHUB_PULL_REQUEST_REVIEW,
                            "pull-request-review:" + review.path("id").asText(), reviewer.path("id").asText(null),
                            reviewer.path("login").asText(null), null, parseInstant(review.path("submitted_at").asText(null)),
                            pullRequest.path("html_url").asText(resource.getResourceUrl()), review.toString());
                }
            }
            context.advance(CollectionPhase.PULL_REQUESTS, pullRequestNumber);
        }
    }

    private void collectIssues(
            IntegrationResource resource,
            String repositoryPath,
            String since,
            String token,
            CollectionCursor cursor,
            CollectionContext context
    ) {
        // since는 updated_at 기준으로 서버에서 거른다. created 오름차순 정렬은 재개용이다.
        for (JsonNode issue : getPages("/repos/" + repositoryPath
                + "/issues?state=all&per_page=100&sort=created&direction=asc&since=" + since, token, context)) {
            int issueNumber = issue.path("number").asInt();
            if (cursor.skipsItem(CollectionPhase.ISSUES, issueNumber)) {
                continue;
            }
            String number = issue.path("number").asText();
            if (!issue.has("pull_request")) {
                JsonNode author = issue.path("user");
                activityStoreService.store(resource, IntegrationActivityType.GITHUB_ISSUE,
                        "issue:" + number, author.path("id").asText(null), author.path("login").asText(null), null,
                        parseInstant(issue.path("created_at").asText(null)), issue.path("html_url").asText(resource.getResourceUrl()),
                        issue.toString());
            }
            // 이슈 payload가 코멘트 수를 알려준다. 0이면 호출 자체가 낭비다.
            if (issue.path("comments").asInt(0) > 0) {
                for (JsonNode comment : getPages("/repos/" + repositoryPath + "/issues/" + number
                        + "/comments?per_page=100&since=" + since, token, context)) {
                    JsonNode commenter = comment.path("user");
                    activityStoreService.store(resource, IntegrationActivityType.GITHUB_ISSUE_COMMENT,
                            "issue-comment:" + comment.path("id").asText(), commenter.path("id").asText(null),
                            commenter.path("login").asText(null), null, parseInstant(comment.path("created_at").asText(null)),
                            comment.path("html_url").asText(issue.path("html_url").asText(resource.getResourceUrl())), comment.toString());
                }
            }
            for (JsonNode event : getPages("/repos/" + repositoryPath + "/issues/" + number + "/events?per_page=100", token, context)) {
                JsonNode actor = event.path("actor");
                activityStoreService.store(resource, IntegrationActivityType.GITHUB_ISSUE_EVENT,
                        "issue-event:" + event.path("id").asText(), actor.path("id").asText(null), actor.path("login").asText(null), null,
                        parseInstant(event.path("created_at").asText(null)), issue.path("html_url").asText(resource.getResourceUrl()), event.toString());
            }
            context.advance(CollectionPhase.ISSUES, issueNumber);
        }
    }

    private List<JsonNode> getPages(String pathWithQuery, String token, CollectionContext context) {
        List<JsonNode> results = new ArrayList<>();
        for (int page = 1; page <= MAX_API_PAGE_COUNT; page++) {
            JsonNode body = get(pathWithQuery + "&page=" + page, token, context);
            if (body == null || !body.isArray() || body.isEmpty()) {
                return results;
            }
            body.forEach(results::add);
            if (body.size() < 100) {
                return results;
            }
        }
        log.warn("GitHub pagination exceeded max page count. path={}, maxPages={}", pathWithQuery, MAX_API_PAGE_COUNT);
        throw new ProviderResourceAccessException(503, null);
    }

    private JsonNode get(String path, String token, CollectionContext context) {
        // 항목 경계가 없는 페이지네이션 구간에서도 잡이 회수되지 않게 한다.
        context.heartbeat();
        rateLimiter.acquire();
        try {
            ResponseEntity<JsonNode> response = restClient.get().uri(URI.create(API_BASE_URL + path))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve().toEntity(JsonNode.class);
            rateLimiter.observe(response.getHeaders());
            return response.getBody();
        } catch (RestClientResponseException exception) {
            log.warn("GitHub API returned error response. path={}, status={}, body={}",
                    path, exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            throw new ProviderResourceAccessException(exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            log.warn("GitHub API call failed without a response (timeout/connection issue). path={}", path, exception);
            throw new ProviderResourceAccessException(503, exception);
        }
    }

    private String repositoryPath(String resourceUrl) {
        try {
            String path = URI.create(resourceUrl).getPath();
            if (path != null && path.matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?")) {
                return path.substring(1).replaceAll("/$", "");
            }
        } catch (RuntimeException ignored) {
            // validation below returns an integration resource error through the collection coordinator.
        }
        throw new ProviderResourceAccessException(404, null);
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}