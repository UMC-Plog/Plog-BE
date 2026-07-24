package com.plog.domain.integration.service;

import com.plog.domain.integration.config.GithubIntegrationProperties;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class GithubIntegrationService {
    private final GithubIntegrationProperties properties;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final IntegrationAuthorizationStateService authorizationStateService;
    private final ProjectIntegrationService projectIntegrationService;
    private final GithubAppClient githubAppClient;

    @Transactional
    public IntegrationAuthorizationResponse issueAuthorizationUrl(Long projectId, Long userId) {
        requireProject(projectId);
        ProjectMember member = projectAccessService.requireActiveMember(projectId, userId);
        projectIntegrationService.requireNotConnected(projectId, LinkType.GITHUB);
        IntegrationAuthorizationStateService.IssuedState state = authorizationStateService.issue(member, LinkType.GITHUB);
        String authorizationUrl = UriComponentsBuilder
                .fromUriString("https://github.com/apps/" + require(properties.appSlug()) + "/installations/new")
                .queryParam("state", state.value())
                .build(true)
                .toUriString();
        return new IntegrationAuthorizationResponse(LinkType.GITHUB, authorizationUrl, state.expiresAt());
    }

    @Transactional
    public IntegrationConnectionResponse completeCallback(String state, String installationId) {
        IntegrationAuthorizationState authorizationState = authorizationStateService.consume(state, LinkType.GITHUB);
        projectIntegrationService.requireNotConnected(authorizationState.getProject().getId(), LinkType.GITHUB);
        if (installationId == null || installationId.isBlank()) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED);
        }
        GithubAppClient.Installation installation = githubAppClient.installation(installationId);
        ProjectIntegration integration = projectIntegrationService.connect(
                authorizationState.getProjectMember(),
                LinkType.GITHUB,
                IntegrationCredentialType.APP_INSTALLATION,
                installation.accountId(),
                installation.accountLogin(),
                installation.id(),
                null,
                null,
                null
        );
        return new IntegrationConnectionResponse(
                integration.getProject().getId(),
                integration.getLinkType(),
                integration.getExternalAccountName()
        );
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private String require(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_CONFIGURATION_ERROR);
        }
        return value;
    }
}
