package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class GithubAppClient {
    private static final String API_BASE_URL = "https://api.github.com";

    private final GithubAppJwtFactory appJwtFactory;
    private final RestClient restClient = RestClient.builder().baseUrl(API_BASE_URL).build();

    public Installation installation(String installationId) {
        JsonNode body = getWithAppJwt("/app/installations/" + installationId);
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

    private JsonNode getWithAppJwt(String path) {
        try {
            return restClient.get()
                    .uri(path)
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
