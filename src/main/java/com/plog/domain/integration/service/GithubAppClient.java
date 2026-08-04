package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GithubAppClient {
    private static final String API_BASE_URL = "https://api.github.com";
    private static final int MAX_REPOSITORY_PAGE_COUNT = 50;

    private final GithubAppJwtFactory appJwtFactory;
    private final RestClient restClient = ProviderRestClientFactory.create(API_BASE_URL);

    public Installation installation(String installationId) {
        JsonNode body = getWithAppJwt("/app/installations/{installationId}", installationId);
        if (body == null) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED);
        }
        JsonNode account = body.path("account");
        return new Installation(
                body.path("id").asText(),
                account.path("id").asText(),
                account.path("login").asText()
        );
    }

    public String createInstallationAccessToken(String installationId) {
        try {
            JsonNode body = restClient.post()
                    .uri("/app/installations/{installationId}/access_tokens", installationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwtFactory.create())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.path("token").asText().isBlank()) {
                throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED);
            }
            return body.path("token").asText();
        } catch (RestClientResponseException exception) {
            log.warn("GitHub installation access token issue failed. installationId={}, status={}, body={}",
                    installationId, exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        } catch (RestClientException exception) {
            log.warn("GitHub installation access token call failed without a response (timeout/connection issue). installationId={}",
                    installationId, exception);
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        }
    }

    public RepositoryListing listInstallationRepositories(String installationId) {
        String accessToken = createInstallationAccessToken(installationId);
        try {
            List<Repository> repositories = new ArrayList<>();
            for (int page = 1; page <= MAX_REPOSITORY_PAGE_COUNT; page++) {
                JsonNode body = restClient.get()
                        .uri("/installation/repositories?per_page=100&page={page}", page)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .retrieve()
                        .body(JsonNode.class);
                if (body == null || !body.path("repositories").isArray() || body.path("repositories").isEmpty()) {
                    return new RepositoryListing(repositories, true);
                }
                for (JsonNode repository : body.path("repositories")) {
                    String id = repository.path("id").asText();
                    if (id.isBlank()) {
                        continue;
                    }
                    repositories.add(new Repository(
                            id,
                            repository.path("full_name").asText(repository.path("name").asText(id)),
                            repository.path("html_url").asText(null),
                            repository.toString(),
                            parseInstant(repository.path("updated_at").asText(null))
                    ));
                }
                if (body.path("repositories").size() < 100) {
                    return new RepositoryListing(repositories, true);
                }
            }
            return new RepositoryListing(repositories, false);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("GitHub installation repositories listing failed. installationId={}, status={}, body={}",
                    installationId, status, ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            if (status == 401 || status == 403) {
                throw new ApiException(IntegrationErrorCode.PROVIDER_RESOURCE_ACCESS_DENIED, exception);
            }
            throw new ApiException(IntegrationErrorCode.PROVIDER_TEMPORARILY_UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            log.warn("GitHub installation repositories listing call failed without a response (timeout/connection issue). installationId={}",
                    installationId, exception);
            throw new ApiException(IntegrationErrorCode.PROVIDER_TEMPORARILY_UNAVAILABLE, exception);
        }
    }

    public IntegrationVerificationStatus verifyInstallation(String installationId) {
        if (installationId == null || installationId.isBlank()) {
            return IntegrationVerificationStatus.DISCONNECTED;
        }
        try {
            restClient.get()
                    .uri("/app/installations/{installationId}", installationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwtFactory.create())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .toBodilessEntity();
            return IntegrationVerificationStatus.VERIFIED;
        } catch (RestClientResponseException exception) {
            log.warn("GitHub installation verification failed. installationId={}, status={}, body={}",
                    installationId, exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            return exception.getStatusCode().value() == 404
                    ? IntegrationVerificationStatus.DISCONNECTED
                    : IntegrationVerificationStatus.UNAVAILABLE;
        } catch (RestClientException exception) {
            log.warn("GitHub installation verification call failed without a response (timeout/connection issue). installationId={}",
                    installationId, exception);
            return IntegrationVerificationStatus.UNAVAILABLE;
        } catch (Exception exception) {
            log.warn("GitHub installation verification failed unexpectedly. installationId={}", installationId, exception);
            return IntegrationVerificationStatus.UNAVAILABLE;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private JsonNode getWithAppJwt(String uriTemplate, Object... uriVariables) {
        try {
            return restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwtFactory.create())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn("GitHub App JWT-authenticated call failed. uri={}, status={}, body={}",
                    uriTemplate, exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        } catch (RestClientException exception) {
            log.warn("GitHub App JWT-authenticated call failed without a response (timeout/connection issue). uri={}",
                    uriTemplate, exception);
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        }
    }

    public record Installation(String id, String accountId, String accountLogin) {}

    public record RepositoryListing(List<Repository> repositories, boolean complete) {}

    public record Repository(String id, String fullName, String htmlUrl, String payload, Instant lastModifiedAt) {}
}