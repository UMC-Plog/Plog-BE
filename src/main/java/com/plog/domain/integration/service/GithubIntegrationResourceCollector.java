package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.entity.CollectionPhase;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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

    private final GithubAppClient githubAppClient;
    private final IntegrationActivityStoreService activityStoreService;
    private final GithubApiRateLimiter rateLimiter;
    private final RestClient restClient;

    /** 생성자가 둘이라 Spring이 주입 대상을 고를 수 없다. 이쪽이 운영용이다. */
    @Autowired
    GithubIntegrationResourceCollector(
            GithubAppClient githubAppClient,
            IntegrationActivityStoreService activityStoreService,
            GithubApiRateLimiter rateLimiter
    ) {
        this(githubAppClient, activityStoreService, rateLimiter,
                ProviderRestClientFactory.create());
    }

    /** 테스트에서 MockRestServiceServer를 물리기 위한 생성자다. */
    GithubIntegrationResourceCollector(
            GithubAppClient githubAppClient,
            IntegrationActivityStoreService activityStoreService,
            GithubApiRateLimiter rateLimiter,
            RestClient restClient
    ) {
        this.githubAppClient = githubAppClient;
        this.activityStoreService = activityStoreService;
        this.rateLimiter = rateLimiter;
        this.restClient = restClient;
    }

    @Override
    public List<LinkType> providers() {
        return List.of(LinkType.GITHUB);
    }

    @Override
    public void collect(
            IntegrationResource resource,
            ProjectIntegration verifiedIntegration,
            CollectionContext context
    ) {
        String accessToken = githubAppClient.createInstallationAccessToken(
                verifiedIntegration.getProviderConnectionId());
        String repositoryPath = repositoryPath(resource.getResourceUrl());

        // 재개 대상 리소스가 아니면 커서를 무시하고 처음부터 수집한다.
        CollectionCursor cursor = context.cursor().resumesResource(resource.getId())
                ? context.cursor()
                : CollectionCursor.start();

        if (!cursor.skipsPhase(CollectionPhase.COMMITS)) {
            collectCommits(resource, repositoryPath, accessToken, context);
        }
        if (!cursor.skipsPhase(CollectionPhase.PULL_REQUESTS)) {
            collectPullRequests(resource, repositoryPath, accessToken, cursor, context);
        }
        if (!cursor.skipsPhase(CollectionPhase.ISSUES)) {
            collectIssues(resource, repositoryPath, accessToken, cursor, context);
        }
    }

    private void collectCommits(IntegrationResource resource, String repositoryPath,
            String token, CollectionContext context) {
        Set<String> storedShas = new HashSet<>();
        for (String refSha : commitRefShas(repositoryPath, token, context)) {
            collectPagedItems("/repos/" + repositoryPath + "/commits?per_page=100&sha="
                    + encodeQueryParam(refSha), token, context, commit -> {
                String commitSha = commit.path("sha").asText(null);
                if (commitSha == null || commitSha.isBlank() || !storedShas.add(commitSha)) {
                    return;
                }
                JsonNode author = commit.path("author");
                JsonNode authorCommit = commit.path("commit").path("author");
                activityStoreService.store(resource, IntegrationActivityType.GITHUB_COMMIT,
                        "commit:" + commitSha, author.path("id").asText(null), author.path("login").asText(null),
                        authorCommit.path("email").asText(null), parseInstant(authorCommit.path("date").asText(null)),
                        commit.path("html_url").asText(resource.getResourceUrl()), commit.toString());
            });
        }
    }

    private Set<String> commitRefShas(String repositoryPath, String token, CollectionContext context) {
        Set<String> refs = new LinkedHashSet<>();
        collectRefShas(refs, repositoryPath, "heads", token, context);
        collectRefShas(refs, repositoryPath, "tags", token, context);
        return refs;
    }

    private void collectRefShas(Set<String> refs, String repositoryPath, String refType, String token,
            CollectionContext context) {
        JsonNode matchingRefs = get("/repos/" + repositoryPath + "/git/matching-refs/" + refType, token, context);
        if (matchingRefs == null || !matchingRefs.isArray()) {
            return;
        }
        for (JsonNode ref : matchingRefs) {
            String sha = ref.path("object").path("sha").asText(null);
            if ("tag".equals(ref.path("object").path("type").asText(null))) {
                sha = annotatedTagTargetSha(repositoryPath, sha, token, context);
            }
            if (sha != null && !sha.isBlank()) {
                refs.add(sha);
            }
        }
    }

    private String annotatedTagTargetSha(String repositoryPath, String tagSha, String token, CollectionContext context) {
        Set<String> visited = new HashSet<>();
        String currentSha = tagSha;
        while (currentSha != null && !currentSha.isBlank()) {
            if (!visited.add(currentSha)) {
                log.warn("GitHub annotated tag loop detected. repository={}, tagSha={}", repositoryPath, tagSha);
                return null;
            }
            JsonNode tag = get("/repos/" + repositoryPath + "/git/tags/" + currentSha, token, context);
            if (tag == null) {
                return null;
            }
            JsonNode target = tag.path("object");
            String targetSha = target.path("sha").asText(null);
            String targetType = target.path("type").asText(null);
            if ("commit".equals(targetType)) {
                return targetSha;
            }
            if (!"tag".equals(targetType)) {
                return null;
            }
            currentSha = targetSha;
        }
        return null;
    }

    private void collectPullRequests(
            IntegrationResource resource,
            String repositoryPath,
            String token,
            CollectionCursor cursor,
            CollectionContext context
    ) {
        // /pulls는 since를 지원하지 않는다. created 오름차순으로 고정해 재개 지점을 안정시키고,
        // 리스트 페이지는 100건 단위라 비용이 작다.
        collectPagedItems("/repos/" + repositoryPath + "/pulls?state=all&per_page=100&sort=created&direction=asc",
                token, context, pullRequest -> {
            int pullRequestNumber = pullRequest.path("number").asInt();
            if (cursor.skipsItem(CollectionPhase.PULL_REQUESTS, pullRequestNumber)) {
                return;
            }
            JsonNode author = pullRequest.path("user");
            String number = pullRequest.path("number").asText();
            activityStoreService.store(resource, IntegrationActivityType.GITHUB_PULL_REQUEST,
                    "pull-request:" + number, author.path("id").asText(null), author.path("login").asText(null), null,
                    parseInstant(pullRequest.path("created_at").asText(null)), pullRequest.path("html_url").asText(resource.getResourceUrl()),
                    pullRequest.toString());
            collectPagedItems("/repos/" + repositoryPath + "/pulls/" + number + "/reviews?per_page=100",
                    token, context, review -> {
                JsonNode reviewer = review.path("user");
                activityStoreService.store(resource, IntegrationActivityType.GITHUB_PULL_REQUEST_REVIEW,
                        "pull-request-review:" + review.path("id").asText(), reviewer.path("id").asText(null),
                        reviewer.path("login").asText(null), null, parseInstant(review.path("submitted_at").asText(null)),
                        pullRequest.path("html_url").asText(resource.getResourceUrl()), review.toString());
            });
            context.advance(CollectionPhase.PULL_REQUESTS, pullRequestNumber);
        });
    }

    private void collectIssues(
            IntegrationResource resource,
            String repositoryPath,
            String token,
            CollectionCursor cursor,
            CollectionContext context
    ) {
        // created 오름차순 정렬은 재개용이다.
        collectPagedItems("/repos/" + repositoryPath
                + "/issues?state=all&per_page=100&sort=created&direction=asc", token, context, issue -> {
            int issueNumber = issue.path("number").asInt();
            if (cursor.skipsItem(CollectionPhase.ISSUES, issueNumber)) {
                return;
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
                collectPagedItems("/repos/" + repositoryPath + "/issues/" + number
                        + "/comments?per_page=100", token, context, comment -> {
                    JsonNode commenter = comment.path("user");
                    activityStoreService.store(resource, IntegrationActivityType.GITHUB_ISSUE_COMMENT,
                            "issue-comment:" + comment.path("id").asText(), commenter.path("id").asText(null),
                            commenter.path("login").asText(null), null, parseInstant(comment.path("created_at").asText(null)),
                            comment.path("html_url").asText(issue.path("html_url").asText(resource.getResourceUrl())), comment.toString());
                });
            }
            collectPagedItems("/repos/" + repositoryPath + "/issues/" + number + "/events?per_page=100",
                    token, context, event -> {
                JsonNode actor = event.path("actor");
                activityStoreService.store(resource, IntegrationActivityType.GITHUB_ISSUE_EVENT,
                        "issue-event:" + event.path("id").asText(), actor.path("id").asText(null), actor.path("login").asText(null), null,
                        parseInstant(event.path("created_at").asText(null)), issue.path("html_url").asText(resource.getResourceUrl()), event.toString());
            });
            context.advance(CollectionPhase.ISSUES, issueNumber);
        });
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void collectPagedItems(String pathWithQuery, String token, CollectionContext context,
            Consumer<JsonNode> itemConsumer) {
        String nextPath = appendPage(pathWithQuery, 1);
        Set<String> requestedPaths = new HashSet<>();
        while (nextPath != null) {
            if (!requestedPaths.add(nextPath)) {
                log.warn("GitHub pagination loop detected. path={}", nextPath);
                throw new ProviderResourceAccessException(503, null);
            }
            ResponseEntity<JsonNode> response = getResponse(nextPath, token, context);
            JsonNode body = response.getBody();
            if (body == null || !body.isArray()) {
                return;
            }
            body.forEach(itemConsumer);
            nextPath = nextPagePath(response.getHeaders());
        }
    }

    private String appendPage(String pathWithQuery, int page) {
        return pathWithQuery + (pathWithQuery.contains("?") ? "&" : "?") + "page=" + page;
    }

    private String nextPagePath(HttpHeaders headers) {
        for (String linkHeader : headers.getOrEmpty(HttpHeaders.LINK)) {
            for (String link : linkHeader.split(",")) {
                if (!link.contains("rel=\"next\"") && !link.contains("rel=next")) {
                    continue;
                }
                int start = link.indexOf('<');
                int end = link.indexOf('>');
                if (start < 0 || end <= start) {
                    continue;
                }
                URI nextUri = URI.create(link.substring(start + 1, end));
                if (!"https".equalsIgnoreCase(nextUri.getScheme())
                        || !"api.github.com".equalsIgnoreCase(nextUri.getHost())
                        || (nextUri.getPort() != -1 && nextUri.getPort() != 443)) {
                    log.warn("GitHub pagination returned an unexpected next URI. uri={}", nextUri);
                    throw new ProviderResourceAccessException(503, null);
                }
                return nextUri.getRawPath() + (nextUri.getRawQuery() == null ? "" : "?" + nextUri.getRawQuery());
            }
        }
        return null;
    }

    private JsonNode get(String path, String token, CollectionContext context) {
        return getResponse(path, token, context).getBody();
    }

    private ResponseEntity<JsonNode> getResponse(String path, String token, CollectionContext context) {
        // 항목 경계가 없는 페이지네이션 구간에서도 잡이 회수되지 않게 한다.
        context.heartbeat();
        rateLimiter.acquire();
        try {
            ResponseEntity<JsonNode> response = restClient.get().uri(URI.create(API_BASE_URL + path))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve().toEntity(JsonNode.class);
            rateLimiter.observe(response.getHeaders());
            return response;
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
