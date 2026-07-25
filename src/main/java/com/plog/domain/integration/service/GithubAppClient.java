package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
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
}
