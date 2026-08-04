package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.config.NotionIntegrationProperties;
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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionIntegrationService {
    private static final String NOTION_VERSION = "2026-03-11";
    private final NotionIntegrationProperties properties;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final IntegrationAuthorizationStateService authorizationStateService;
    private final ProjectIntegrationService projectIntegrationService;
    private final RestClient restClient = ProviderRestClientFactory.create();

    @Transactional
    public IntegrationAuthorizationResponse issueAuthorizationUrl(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        projectIntegrationService.requireNotConnected(projectId, LinkType.NOTION);
        IntegrationAuthorizationStateService.IssuedState state = authorizationStateService.issue(member, LinkType.NOTION);
        String url = UriComponentsBuilder.fromUriString("https://api.notion.com/v1/oauth/authorize")
                .queryParam("owner", "user")
                .queryParam("client_id", require(properties.clientId()))
                .queryParam("redirect_uri", require(properties.callbackUrl()))
                .queryParam("response_type", "code")
                .queryParam("state", state.value())
                .build()
                .encode()
                .toUriString();
        return new IntegrationAuthorizationResponse(LinkType.NOTION, url, state.expiresAt());
    }

    public IntegrationConnectionResponse completeCallback(String state, String code) {
        IntegrationAuthorizationState authorizationState = authorizationStateService.consume(state, LinkType.NOTION);
        projectIntegrationService.requireNotConnected(
                authorizationState.getProjectMember().getProject().getId(), LinkType.NOTION);
        JsonNode token = exchangeCode(code);
        String workspaceId = requiredField(token, "workspace_id");
        String workspaceName = token.path("workspace_name").asText(workspaceId);
        String botId = token.path("bot_id").asText(workspaceId);
        ProjectIntegration integration = projectIntegrationService.connect(
                authorizationState.getProjectMember(),
                LinkType.NOTION,
                IntegrationCredentialType.OAUTH,
                workspaceId,
                workspaceName,
                botId,
                requiredField(token, "access_token"),
                token.path("refresh_token").asText(null),
                null
        );
        return new IntegrationConnectionResponse(
                integration.getProject().getId(),
                LinkType.NOTION,
                integration.getExternalAccountName()
        );
    }

    public IntegrationVerificationStatus verifyConnection(ProjectIntegration integration) {
        String accessToken;
        try {
            accessToken = projectIntegrationService.decryptAccessToken(integration);
        } catch (ApiException exception) {
            return IntegrationVerificationStatus.UNAVAILABLE;
        }
        TokenCheck tokenCheck = checkAccessToken(accessToken);
        if (tokenCheck == TokenCheck.VERIFIED) {
            return IntegrationVerificationStatus.VERIFIED;
        }
        if (tokenCheck == TokenCheck.UNAVAILABLE) {
            return IntegrationVerificationStatus.UNAVAILABLE;
        }
        return refreshAndVerify(integration);
    }

    private JsonNode exchangeCode(String code) {
        try {
            return restClient.post()
                    .uri("https://api.notion.com/v1/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBasicAuth(require(properties.clientId()), require(properties.clientSecret())))
                    .body(Map.of(
                            "grant_type", "authorization_code",
                            "code", require(code),
                            "redirect_uri", require(properties.callbackUrl())
                    ))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn("Notion OAuth token exchange failed. status={}, body={}",
                    exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        } catch (RestClientException exception) {
            log.warn("Notion OAuth token exchange call failed without a response (timeout/connection issue).", exception);
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        }
    }

    private IntegrationVerificationStatus refreshAndVerify(ProjectIntegration integration) {
        String refreshToken;
        try {
            refreshToken = projectIntegrationService.decryptRefreshToken(integration);
        } catch (ApiException exception) {
            return IntegrationVerificationStatus.UNAVAILABLE;
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            return IntegrationVerificationStatus.DISCONNECTED;
        }
        try {
            JsonNode token = restClient.post()
                    .uri("https://api.notion.com/v1/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBasicAuth(require(properties.clientId()), require(properties.clientSecret())))
                    .body(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken))
                    .retrieve()
                    .body(JsonNode.class);
            String accessToken = token == null ? null : token.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                return IntegrationVerificationStatus.UNAVAILABLE;
            }
            TokenCheck tokenCheck = checkAccessToken(accessToken);
            if (tokenCheck != TokenCheck.VERIFIED) {
                return tokenCheck == TokenCheck.AUTHENTICATION_FAILED
                        ? IntegrationVerificationStatus.DISCONNECTED
                        : IntegrationVerificationStatus.UNAVAILABLE;
            }
            String nextRefreshToken = token.path("refresh_token").asText(refreshToken);
            projectIntegrationService.rotateOAuthTokens(integration.getId(), accessToken, nextRefreshToken, null);
            return IntegrationVerificationStatus.VERIFIED;
        } catch (RestClientResponseException exception) {
            log.warn("Notion OAuth token refresh failed. integrationId={}, status={}, body={}",
                    integration.getId(), exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            return isInvalidGrant(exception)
                    ? IntegrationVerificationStatus.DISCONNECTED
                    : IntegrationVerificationStatus.UNAVAILABLE;
        } catch (RestClientException | ApiException exception) {
            log.warn("Notion OAuth token refresh call failed without a usable response. integrationId={}",
                    integration.getId(), exception);
            return IntegrationVerificationStatus.UNAVAILABLE;
        }
    }

    private TokenCheck checkAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return TokenCheck.AUTHENTICATION_FAILED;
        }
        try {
            restClient.get()
                    .uri("https://api.notion.com/v1/users/me")
                    .header("Notion-Version", NOTION_VERSION)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .toBodilessEntity();
            return TokenCheck.VERIFIED;
        } catch (RestClientResponseException exception) {
            log.warn("Notion access token check failed. status={}, body={}",
                    exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            return exception.getStatusCode().value() == 401
                    ? TokenCheck.AUTHENTICATION_FAILED
                    : TokenCheck.UNAVAILABLE;
        } catch (RestClientException exception) {
            log.warn("Notion access token check call failed without a response (timeout/connection issue).", exception);
            return TokenCheck.UNAVAILABLE;
        }
    }

    private boolean isInvalidGrant(RestClientResponseException exception) {
        return exception.getStatusCode().value() == 400
                && exception.getResponseBodyAsString().contains("invalid_grant");
    }

    private ProjectMember requireMember(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        return projectAccessService.requireActiveMember(projectId, userId);
    }

    private String requiredField(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText();
        return require(value);
    }

    private String require(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_CONFIGURATION_ERROR);
        }
        return value;
    }

    private enum TokenCheck {
        VERIFIED,
        AUTHENTICATION_FAILED,
        UNAVAILABLE
    }
}