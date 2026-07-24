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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class NotionIntegrationService {
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

    @Transactional
    public IntegrationConnectionResponse completeCallback(String state, String code) {
        IntegrationAuthorizationState authorizationState = authorizationStateService.consume(state, LinkType.NOTION);
        JsonNode token = exchangeCode(code);
        String workspaceId = requiredField(token, "workspace_id");
        String workspaceName = token.path("workspace_name").asText(workspaceId);
        String botId = token.path("bot_id").asText(workspaceId);
        projectIntegrationService.requireNotConnected(authorizationState.getProject().getId(), LinkType.NOTION);
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
        } catch (RestClientException exception) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED, exception);
        }
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
}
