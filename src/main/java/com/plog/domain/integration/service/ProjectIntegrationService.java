package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectIntegrationService {
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationResourceRepository integrationResourceRepository;
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
        Long projectId = projectMember.getProject().getId();
        requireMutableProject(projectId);
        ProjectIntegration existingIntegration = projectIntegrationRepository
                .findByProjectIdAndLinkTypeForUpdate(projectId, linkType)
                .orElse(null);
        if (existingIntegration != null && existingIntegration.isConnected()) {
            throw new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_ALREADY_CONNECTED);
        }
        String encryptedAccessToken = encrypt(accessToken);
        String encryptedRefreshToken = encrypt(refreshToken);
        if (existingIntegration != null) {
            existingIntegration.updateConnection(
                    projectMember,
                    credentialType,
                    externalAccountId,
                    externalAccountName,
                    providerConnectionId,
                    encryptedAccessToken,
                    encryptedRefreshToken,
                    accessTokenExpiresAt
            );
            integrationResourceRepository.findAllByProjectIntegrationIdOrderByIdAsc(existingIntegration.getId())
                    .forEach(IntegrationResource::activate);
            return existingIntegration;
        }
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

    @Transactional
    public void disconnect(Long projectId, LinkType linkType) {
        ProjectIntegration integration = projectIntegrationRepository
                .findByProjectIdAndLinkTypeForUpdate(projectId, linkType)
                .filter(ProjectIntegration::canDisconnect)
                .orElseThrow(() -> new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_NOT_FOUND));
        integration.disconnect();
        Instant now = Instant.now();
        integrationResourceRepository.findAllByProjectIntegrationIdOrderByIdAsc(integration.getId())
                .forEach(resource -> resource.disable(now));
    }

    /** 상위 트랜잭션이 이후 예외로 롤백되더라도 재인증 필요 상태는 반드시 반영되어야 하므로 별도 트랜잭션으로 커밋한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireReauthorization(Long integrationId) {
        projectIntegrationRepository.findByIdForUpdate(integrationId)
                .filter(ProjectIntegration::isConnected)
                .ifPresent(integration -> {
                    integration.requireReauthorization();
                    Instant now = Instant.now();
                    integrationResourceRepository.findAllByProjectIntegrationIdOrderByIdAsc(integrationId)
                            .forEach(resource -> resource.requireReauthorization(now));
                });
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
        projectIntegrationRepository.findByIdForUpdate(integrationId)
                .filter(ProjectIntegration::isConnected)
                .ifPresent(integration -> integration.updateOAuthTokens(
                        encrypt(accessToken), encrypt(refreshToken), accessTokenExpiresAt));
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

    private void requireMutableProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));
        if (project.isCompleted()) {
            throw new ApiException(IntegrationErrorCode.WORKSPACE_INTEGRATION_LOCKED);
        }
    }
}