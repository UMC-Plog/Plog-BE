package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GithubAppClient {
    private static final String API_BASE_URL = "https://api.github.com";

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
        } catch (RestClientException exception) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        }
    }

    public List<Repository> listInstallationRepositories(String installationId) {
        String accessToken = createInstallationAccessToken(installationId);
        try {
            List<Repository> repositories = new ArrayList<>();
            for (int page = 1; ; page++) {
                JsonNode body = restClient.get()
                        .uri("/installation/repositories?per_page=100&page={page}", page)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .retrieve()
                        .body(JsonNode.class);
                if (body == null || !body.path("repositories").isArray() || body.path("repositories").isEmpty()) {
                    return repositories;
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
                    return repositories;
                }
            }
        } catch (RestClientException exception) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_RESOURCE_ACCESS_DENIED, exception);
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
            return exception.getStatusCode().value() == 404
                    ? IntegrationVerificationStatus.DISCONNECTED
                    : IntegrationVerificationStatus.UNAVAILABLE;
        } catch (RestClientException exception) {
            return IntegrationVerificationStatus.UNAVAILABLE;
        } catch (Exception exception) {
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
        } catch (RestClientException exception) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        }
    }

    public record Installation(String id, String accountId, String accountLogin) {}

    public record Repository(String id, String fullName, String htmlUrl, String payload, Instant lastModifiedAt) {}
}
