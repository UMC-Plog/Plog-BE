package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationConnectionStatus;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IntegrationVerificationService {

    private final GithubAppClient githubAppClient;
    private final FigmaIntegrationService figmaIntegrationService;
    private final NotionIntegrationService notionIntegrationService;
    private final GoogleIntegrationService googleIntegrationService;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final ProjectIntegrationService projectIntegrationService;

    /** 리소스 등록·동기화 전에 실제 provider 권한을 확인한다. */
    public ProjectIntegration requireVerifiedConnection(Long projectId, LinkType linkType) {
        ProjectIntegration integration = projectIntegrationRepository.findByProjectIdAndLinkType(projectId, linkType)
                .orElseThrow(() -> new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_NOT_FOUND));

        if (integration.getConnectionStatus() == IntegrationConnectionStatus.REAUTH_REQUIRED) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_REAUTHORIZATION_REQUIRED);
        }
        if (!integration.isConnected()) {
            throw new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_NOT_FOUND);
        }

        IntegrationVerificationStatus status = verify(integration);
        if (status == IntegrationVerificationStatus.VERIFIED) {
            return projectIntegrationRepository.findById(integration.getId())
                    .orElseThrow(() -> new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_NOT_FOUND));
        }
        if (status == IntegrationVerificationStatus.DISCONNECTED) {
            projectIntegrationService.requireReauthorization(integration.getId());
            throw new ApiException(IntegrationErrorCode.PROVIDER_REAUTHORIZATION_REQUIRED);
        }
        if (status == IntegrationVerificationStatus.UNAVAILABLE) {
            throw new ApiException(IntegrationErrorCode.PROVIDER_TEMPORARILY_UNAVAILABLE);
        }
        throw new ApiException(IntegrationErrorCode.PROVIDER_AUTHORIZATION_FAILED);
    }

    private IntegrationVerificationStatus verify(ProjectIntegration integration) {
        if (integration == null || !integration.isConnected()) {
            return IntegrationVerificationStatus.NOT_CONNECTED;
        }
        return switch (integration.getLinkType()) {
            case GITHUB -> githubAppClient.verifyInstallation(integration.getProviderConnectionId());
            case FIGMA -> figmaIntegrationService.verifyConnection(integration);
            case NOTION -> notionIntegrationService.verifyConnection(integration);
            case GOOGLE_DOCS, GOOGLE_SLIDES -> googleIntegrationService.verifyConnection(integration);
        };
    }
}