package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectIntegrationService {
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationCredentialCipher credentialCipher;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public void requireNotConnected(Long projectId, LinkType linkType) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));
        if (project.isCompleted()) {
            throw new ApiException(IntegrationErrorCode.WORKSPACE_INTEGRATION_LOCKED);
        }
        projectIntegrationRepository.findByProjectIdAndLinkType(projectId, linkType)
                .ifPresent(integration -> {
                    throw new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_ALREADY_CONNECTED);
                });
    }

    @Transactional
    public ProjectIntegration connect(
            ProjectMember projectMember,
            LinkType linkType,
            IntegrationCredentialType credentialType,
            String externalAccountId,
            String externalAccountName,
            String providerConnectionId,
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiresAt
    ) {
        requireNotConnected(projectMember.getProject().getId(), linkType);
        String encryptedAccessToken = encrypt(accessToken);
        String encryptedRefreshToken = encrypt(refreshToken);
        try {
            return projectIntegrationRepository.saveAndFlush(ProjectIntegration.builder()
                        .project(projectMember.getProject())
                        .connectedByProjectMember(projectMember)
                        .linkType(linkType)
                        .credentialType(credentialType)
                        .externalAccountId(externalAccountId)
                        .externalAccountName(externalAccountName)
                        .providerConnectionId(providerConnectionId)
                        .accessTokenEncrypted(encryptedAccessToken)
                        .refreshTokenEncrypted(encryptedRefreshToken)
                        .accessTokenExpiresAt(accessTokenExpiresAt)
                        .build());
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_ALREADY_CONNECTED, exception);
        }
    }

    public String decryptAccessToken(ProjectIntegration integration) {
        return decrypt(integration.getAccessTokenEncrypted());
    }

    public String decryptRefreshToken(ProjectIntegration integration) {
        return decrypt(integration.getRefreshTokenEncrypted());
    }

    @Transactional
    public void rotateOAuthTokens(
            Long integrationId,
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiresAt
    ) {
        projectIntegrationRepository.findById(integrationId)
                .ifPresent(integration -> integration.updateOAuthTokens(
                        encrypt(accessToken), encrypt(refreshToken), accessTokenExpiresAt));
    }

    @Transactional
    public void removeIfPresent(Long integrationId) {
        projectIntegrationRepository.findById(integrationId)
                .ifPresent(projectIntegrationRepository::delete);
    }

    private String encrypt(String value) {
        try {
            return credentialCipher.encrypt(value);
        } catch (IllegalStateException exception) {
            throw new ApiException(IntegrationErrorCode.CREDENTIAL_ENCRYPTION_ERROR, exception);
        }
    }

    private String decrypt(String value) {
        try {
            return credentialCipher.decrypt(value);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new ApiException(IntegrationErrorCode.CREDENTIAL_ENCRYPTION_ERROR, exception);
        }
    }
}
