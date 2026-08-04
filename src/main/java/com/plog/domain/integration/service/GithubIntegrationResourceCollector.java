package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** GitHub App 설치 저장소에서 commit, PR, review, issue 및 issue 활동 원문을 수집한다. */
@Slf4j
@Component
@RequiredArgsConstructor
class GithubIntegrationResourceCollector implements IntegrationResourceCollector {

    private static final String API_BASE_URL = "https://api.github.com";
    private static final int MAX_API_PAGE_COUNT = 100;

    private final GithubAppClient githubAppClient;
    private final IntegrationActivityStoreService activityStoreService;
    private final RestClient restClient = ProviderRestClientFactory.create();

    @Override
    public List<LinkType> providers() {
        return List.of(LinkType.GITHUB);
    }

    @Override
    public void collect(IntegrationResource resource) {
        String accessToken = githubAppClient.createInstallationAccessToken(
                resource.getProjectIntegration().getProviderConnectionId());
        String repositoryPath = repositoryPath(resource.getResourceUrl());
        String since = resource.getProjectIntegration().getProject().getStartDay()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        collectCommits(resource, repositoryPath, since, accessToken);
        collectPullRequests(resource, repositoryPath, accessToken);
        collectIssues(resource, repositoryPath, accessToken);
    }

    private void collectCommits(IntegrationResource resource, String repositoryPath, String since, String token) {
        for (JsonNode commit : getPages("/repos/" + repositoryPath + "/commits?per_page=100&since=" + since, token)) {
            JsonNode author = commit.path("author");
            JsonNode authorCommit = commit.path("commit").path("author");
            activityStoreService.store(resource, IntegrationActivityType.GITHUB_COMMIT,
                    "commit:" + commit.path("sha").asText(), author.path("id").asText(null), author.path("login").asText(null),
                    authorCommit.path("email").asText(null), parseInstant(authorCommit.path("date").asText(null)),
                    commit.path("html_url").asText(resource.getResourceUrl()), commit.toString());
        }
    }

    private void collectPullRequests(IntegrationResource resource, String repositoryPath, String token) {
        for (JsonNode pullRequest : getPages("/repos/" + repositoryPath + "/pulls?state=all&per_page=100", token)) {
            JsonNode author = pullRequest.path("user");
            String number = pullRequest.path("number").asText();
            activityStoreService.store(resource, IntegrationActivityType.GITHUB_PULL_REQUEST,
                    "pull-request:" + number, author.path("id").asText(null), author.path("login").asText(null), null,
                    parseInstant(pullRequest.path("created_at").asText(null)), pullRequest.path("html_url").asText(resource.getResourceUrl()),
                    pullRequest.toString());
            for (JsonNode review : getPages("/repos/" + repositoryPath + "/pulls/" + number + "/reviews?per_page=100", token)) {
                JsonNode reviewer = review.path("user");
                activityStoreService.store(resource, IntegrationActivityType.GITHUB_PULL_REQUEST_REVIEW,
                        "pull-request-review:" + review.path("id").asText(), reviewer.path("id").asText(null),
                        reviewer.path("login").asText(null), null, parseInstant(review.path("submitted_at").asText(null)),
                        pullRequest.path("html_url").asText(resource.getResourceUrl()), review.toString());
            }
        }
    }

    private void collectIssues(IntegrationResource resource, String repositoryPath, String token) {
        for (JsonNode issue : getPages("/repos/" + repositoryPath + "/issues?state=all&per_page=100", token)) {
            String number = issue.path("number").asText();
            if (!issue.has("pull_request")) {
                JsonNode author = issue.path("user");
                activityStoreService.store(resource, IntegrationActivityType.GITHUB_ISSUE,
                        "issue:" + number, author.path("id").asText(null), author.path("login").asText(null), null,
                        parseInstant(issue.path("created_at").asText(null)), issue.path("html_url").asText(resource.getResourceUrl()),
                        issue.toString());
            }
            for (JsonNode comment : getPages("/repos/" + repositoryPath + "/issues/" + number + "/comments?per_page=100", token)) {
                JsonNode commenter = comment.path("user");
                activityStoreService.store(resource, IntegrationActivityType.GITHUB_ISSUE_COMMENT,
                        "issue-comment:" + comment.path("id").asText(), commenter.path("id").asText(null),
                        commenter.path("login").asText(null), null, parseInstant(comment.path("created_at").asText(null)),
                        comment.path("html_url").asText(issue.path("html_url").asText(resource.getResourceUrl())), comment.toString());
            }
            for (JsonNode event : getPages("/repos/" + repositoryPath + "/issues/" + number + "/events?per_page=100", token)) {
                JsonNode actor = event.path("actor");
                activityStoreService.store(resource, IntegrationActivityType.GITHUB_ISSUE_EVENT,
                        "issue-event:" + event.path("id").asText(), actor.path("id").asText(null), actor.path("login").asText(null), null,
                        parseInstant(event.path("created_at").asText(null)), issue.path("html_url").asText(resource.getResourceUrl()), event.toString());
            }
        }
    }

    private List<JsonNode> getPages(String pathWithQuery, String token) {
        List<JsonNode> results = new ArrayList<>();
        for (int page = 1; page <= MAX_API_PAGE_COUNT; page++) {
            JsonNode body = get(pathWithQuery + "&page=" + page, token);
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

    private JsonNode get(String path, String token) {
        try {
            return restClient.get().uri(URI.create(API_BASE_URL + path))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve().body(JsonNode.class);
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