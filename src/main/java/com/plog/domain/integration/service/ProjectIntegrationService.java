package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectIntegrationService {
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationCredentialCipher credentialCipher;

    @Transactional(readOnly = true)
    public void requireNotConnected(Long projectId, LinkType linkType) {
        projectIntegrationRepository.findByProjectIdAndLinkType(projectId, linkType)
                .filter(ProjectIntegration::isConnected)
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
        String encryptedAccessToken = encrypt(accessToken);
        String encryptedRefreshToken = encrypt(refreshToken);
        return projectIntegrationRepository.findByProjectIdAndLinkType(projectMember.getProject().getId(), linkType)
                .map(integration -> {
                    integration.updateConnection(
                            projectMember,
                            credentialType,
                            externalAccountId,
                            externalAccountName,
                            providerConnectionId,
                            encryptedAccessToken,
                            encryptedRefreshToken,
                            accessTokenExpiresAt
                    );
                    return integration;
                })
                .orElseGet(() -> projectIntegrationRepository.save(ProjectIntegration.builder()
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
                        .build()));
    }

    public String decryptAccessToken(ProjectIntegration integration) {
        return decrypt(integration.getAccessTokenEncrypted());
    }

    public String decryptRefreshToken(ProjectIntegration integration) {
        return decrypt(integration.getRefreshTokenEncrypted());
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
