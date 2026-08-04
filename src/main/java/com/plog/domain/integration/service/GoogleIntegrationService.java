package com.plog.domain.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.integration.config.GoogleIntegrationProperties;
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
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleIntegrationService {
    private static final String DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String[] SCOPES = {
            "openid",
            "email",
            "profile",
            DRIVE_FILE_SCOPE,
            "https://www.googleapis.com/auth/drive.activity.readonly",
            "https://www.googleapis.com/auth/drive.metadata.readonly"
    };

    private final GoogleIntegrationProperties properties;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final IntegrationAuthorizationStateService authorizationStateService;
    private final ProjectIntegrationService projectIntegrationService;
    private final RestClient restClient = ProviderRestClientFactory.create();

    @Transactional
    public IntegrationAuthorizationResponse issueAuthorizationUrl(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        projectIntegrationService.requireNotConnected(projectId, LinkType.GOOGLE);
        IntegrationAuthorizationStateService.IssuedState state = authorizationStateService.issue(member, LinkType.GOOGLE);
        String url = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", require(properties.clientId()))
                .queryParam("redirect_uri", require(properties.callbackUrl()))
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", SCOPES))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state.value())
                .build()
                .encode()
                .toUriString();
        return new IntegrationAuthorizationResponse(LinkType.GOOGLE, url, state.expiresAt());
    }

    public IntegrationConnectionResponse completeCallback(String state, String code) {
        IntegrationAuthorizationState authorizationState = authorizationStateService.consume(state, LinkType.GOOGLE);
        projectIntegrationService.requireNotConnected(
                authorizationState.getProjectMember().getProject().getId(), LinkType.GOOGLE);
        JsonNode token = exchangeCode(code);
        String accessToken = requiredField(token, "access_token");
        String refreshToken = requiredRefreshToken(token);
        JsonNode profile = profile(accessToken);
        String externalAccountId = requiredField(profile, "sub");
        String externalAccountName = profile.path("email").asText(profile.path("name").asText(externalAccountId));
        ProjectIntegration integration = projectIntegrationService.connect(
                authorizationState.getProjectMember(),
                LinkType.GOOGLE,
                IntegrationCredentialType.OAUTH,
                externalAccountId,
                externalAccountName,
                externalAccountId,
                accessToken,
                refreshToken,
                Instant.now().plusSeconds(token.path("expires_in").asLong())
        );
        return new IntegrationConnectionResponse(
                integration.getProject().getId(),
                LinkType.GOOGLE,
                integration.getExternalAccountName()
        );
    }

    public IntegrationVerificationStatus verifyConnection(ProjectIntegration integration) {
        if (integration.getAccessTokenExpiresAt() != null
                && !integration.getAccessTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return refreshAndVerify(integration);
        }
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
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", require(code));
            body.add("client_id", require(properties.clientId()));
            body.add("client_secret", require(properties.clientSecret()));
            body.add("redirect_uri", require(properties.callbackUrl()));
            body.add("grant_type", "authorization_code");
            return restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn("Google OAuth token exchange failed. status={}, body={}",
                    exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        } catch (RestClientException exception) {
            log.warn("Google OAuth token exchange call failed without a response (timeout/connection issue).", exception);
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        }
    }

    private JsonNode profile(String accessToken) {
        try {
            return restClient.get()
                    .uri("https://openidconnect.googleapis.com/v1/userinfo")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn("Google userinfo call failed. status={}, body={}",
                    exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        } catch (RestClientException exception) {
            log.warn("Google userinfo call failed without a response (timeout/connection issue).", exception);
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
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", require(properties.clientId()));
            body.add("client_secret", require(properties.clientSecret()));
            body.add("refresh_token", refreshToken);
            body.add("grant_type", "refresh_token");
            JsonNode token = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
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
            long expiresIn = token.path("expires_in").asLong(0);
            projectIntegrationService.rotateOAuthTokens(
                    integration.getId(), accessToken, nextRefreshToken,
                    expiresIn > 0 ? Instant.now().plusSeconds(expiresIn) : null);
            return IntegrationVerificationStatus.VERIFIED;
        } catch (RestClientResponseException exception) {
            log.warn("Google OAuth token refresh failed. integrationId={}, status={}, body={}",
                    integration.getId(), exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            return isInvalidGrant(exception)
                    ? IntegrationVerificationStatus.DISCONNECTED
                    : IntegrationVerificationStatus.UNAVAILABLE;
        } catch (RestClientException | ApiException exception) {
            log.warn("Google OAuth token refresh call failed without a usable response. integrationId={}",
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
                    .uri("https://www.googleapis.com/drive/v3/about?fields=user(permissionId,emailAddress)")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
            if (!hasDriveFileScope(accessToken)) {
                return TokenCheck.AUTHENTICATION_FAILED;
            }
            return TokenCheck.VERIFIED;
        } catch (RestClientResponseException exception) {
            log.warn("Google Drive access token check failed. status={}, body={}",
                    exception.getStatusCode().value(),
                    ProviderResponseLogSupport.sanitizeForLog(exception.getResponseBodyAsString()));
            return exception.getStatusCode().value() == 401
                    ? TokenCheck.AUTHENTICATION_FAILED
                    : TokenCheck.UNAVAILABLE;
        } catch (RestClientException exception) {
            log.warn("Google Drive access token check call failed without a response (timeout/connection issue).", exception);
            return TokenCheck.UNAVAILABLE;
        }
    }

    private boolean hasDriveFileScope(String accessToken) {
        try {
            JsonNode tokenInfo = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("oauth2.googleapis.com")
                            .path("/tokeninfo")
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            String scopes = tokenInfo == null ? "" : tokenInfo.path("scope").asText("");
            for (String scope : scopes.split("\\s+")) {
                if (DRIVE_FILE_SCOPE.equals(scope)) {
                    return true;
                }
            }
            return false;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 401) {
                return false;
            }
            throw exception;
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

    private String requiredRefreshToken(JsonNode node) {
        String value = node == null ? null : node.path("refresh_token").asText(null);
        if (value == null || value.isBlank()) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED);
        }
        return value;
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