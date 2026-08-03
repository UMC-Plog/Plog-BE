package com.plog.domain.integration.service;

import com.plog.domain.integration.dto.response.GooglePickerAccessTokenResponse;
import com.plog.domain.integration.entity.IntegrationConnectionStatus;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GooglePickerAccessTokenService {

    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationVerificationService integrationVerificationService;
    private final ProjectIntegrationService projectIntegrationService;

    public GooglePickerAccessTokenResponse issue(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        ProjectMember member = projectAccessService.requireActiveMember(projectId, userId);
        ProjectIntegration connectedIntegration = projectIntegrationRepository
                .findByProjectIdAndLinkType(projectId, LinkType.GOOGLE)
                .filter(GooglePickerAccessTokenService::canCheckPickerOwner)
                .orElseThrow(() -> new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_NOT_FOUND));
        if (!Objects.equals(connectedIntegration.getConnectedByProjectMember().getId(), member.getId())) {
            throw new ApiException(IntegrationErrorCode.GOOGLE_PICKER_TOKEN_PERMISSION_DENIED);
        }

        ProjectIntegration integration = integrationVerificationService
                .requireVerifiedConnection(projectId, LinkType.GOOGLE);
        return new GooglePickerAccessTokenResponse(
                projectIntegrationService.decryptAccessToken(integration),
                integration.getExternalAccountName(),
                integration.getAccessTokenExpiresAt()
        );
    }

    private static boolean canCheckPickerOwner(ProjectIntegration integration) {
        return integration.isConnected()
                || integration.getConnectionStatus() == IntegrationConnectionStatus.REAUTH_REQUIRED;
    }
}
