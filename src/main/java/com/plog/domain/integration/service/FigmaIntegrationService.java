package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.config.FigmaIntegrationProperties;
import com.plog.domain.integration.dto.response.IntegrationAuthorizationResponse;
import com.plog.domain.integration.dto.response.IntegrationConnectionResponse;
import com.plog.domain.integration.entity.IntegrationAuthorizationState;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.net.URI;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class FigmaIntegrationService {
    private static final String API_BASE_URL = "https://api.figma.com";
    private static final String[] SCOPES = {
            "current_user:read", "file_metadata:read", "file_content:read",
            "file_versions:read", "file_comments:read"
    };

    private final FigmaIntegrationProperties properties;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final IntegrationAuthorizationStateService authorizationStateService;
    private final ProjectIntegrationService projectIntegrationService;
    private final RestClient restClient = RestClient.create();

    @Transactional
    public IntegrationAuthorizationResponse issueAuthorizationUrl(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        projectIntegrationService.requireNotConnected(projectId, LinkType.FIGMA);
        IntegrationAuthorizationStateService.IssuedState state = authorizationStateService.issue(member, LinkType.FIGMA);
        String url = UriComponentsBuilder.fromUriString("https://www.figma.com/oauth")
                .queryParam("client_id", require(properties.clientId()))
                .queryParam("redirect_uri", require(properties.callbackUrl()))
                .queryParam("scope", String.join(",", SCOPES))
                .queryParam("state", state.value())
                .queryParam("response_type", "code")
                .build(true).toUriString();
        return new IntegrationAuthorizationResponse(LinkType.FIGMA, url, state.expiresAt());
    }

    @Transactional
    public IntegrationConnectionResponse completeCallback(String state, String code) {
        IntegrationAuthorizationState authorizationState = authorizationStateService.consume(state, LinkType.FIGMA);
        JsonNode token = exchangeCode(code);
        String accessToken = requiredField(token, "access_token");
        JsonNode profile = profile(accessToken);
        ProjectIntegration integration = projectIntegrationService.connect(
                authorizationState.getProjectMember(), LinkType.FIGMA, IntegrationCredentialType.OAUTH,
                requiredField(token, "user_id_string"), profile.path("handle").asText(profile.path("email").asText()),
                requiredField(token, "user_id_string"), accessToken, requiredField(token, "refresh_token"),
                Instant.now().plusSeconds(token.path("expires_in").asLong())
        );
        return new IntegrationConnectionResponse(integration.getProject().getId(), LinkType.FIGMA,
                integration.getExternalAccountName());
    }

    private JsonNode exchangeCode(String code) {
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("redirect_uri", require(properties.callbackUrl()));
            body.add("code", require(code));
            body.add("grant_type", "authorization_code");
            return restClient.post().uri("https://api.figma.com/v1/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(headers -> headers.setBasicAuth(require(properties.clientId()), require(properties.clientSecret())))
                    .body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        }
    }

    private JsonNode profile(String accessToken) {
        return get("/v1/me", accessToken);
    }

    private JsonNode get(String path, String accessToken) {
        try {
            return restClient.get().uri(URI.create(API_BASE_URL + path))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_RESOURCE_ACCESS_DENIED, exception);
        }
    }

    private ProjectMember requireMember(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        return projectAccessService.requireActiveMember(projectId, userId);
    }

    private String requiredField(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText();
        return require(value);
    }

    private String require(String value) {
        if (value == null || value.isBlank()) throw new ApiException(IntegrationErrorCode.PROVIDER_CONFIGURATION_ERROR);
        return value;
    }
}
