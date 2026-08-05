package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.entity.IntegrationConnectionStatus;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.*;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectIntegrationServiceTest {

    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;

    @Mock
    private IntegrationResourceRepository integrationResourceRepository;

    @Mock
    private IntegrationActivityRepository integrationActivityRepository;

    @Mock
    private NotionWebhookEventRepository notionWebhookEventRepository;

    @Mock
    private IntegrationCollectionRunRepository integrationCollectionRunRepository;

    @Mock
    private IntegrationCredentialCipher credentialCipher;

    @Mock
    private ProjectRepository projectRepository;

    @Test
    void rejectsStartingWorkspaceIntegrationAfterProjectCompletion() {
        ProjectIntegrationService service = service();
        Project project = mock(Project.class);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(project.isCompleted()).willReturn(true);

        assertThatThrownBy(() -> service.requireNotConnected(1L, LinkType.GITHUB))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(IntegrationErrorCode.WORKSPACE_INTEGRATION_LOCKED));

        verify(projectIntegrationRepository, never()).findByProjectIdAndLinkType(1L, LinkType.GITHUB);
    }

    @Test
    void allowsAuthorizationWhenExistingIntegrationIsDisconnected() {
        ProjectIntegrationService service = service();
        Project project = mock(Project.class);
        ProjectIntegration integration = ProjectIntegration.builder()
                .providerConnectionId("installation-1")
                .connectionStatus(IntegrationConnectionStatus.REVOKED)
                .build();
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(projectIntegrationRepository.findByProjectIdAndLinkType(1L, LinkType.GITHUB))
                .willReturn(Optional.of(integration));

        service.requireNotConnected(1L, LinkType.GITHUB);

        verify(projectIntegrationRepository).findByProjectIdAndLinkType(1L, LinkType.GITHUB);
    }

    @Test
    void reconnectsExistingIntegrationAndReactivatesResources() {
        ProjectIntegrationService service = service();
        Project project = mock(Project.class);
        ProjectMember member = mock(ProjectMember.class);
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(10L)
                .project(project)
                .linkType(LinkType.NOTION)
                .providerConnectionId("old-connection")
                .connectionStatus(IntegrationConnectionStatus.REVOKED)
                .build();
        IntegrationResource resource = IntegrationResource.builder()
                .resourceStatus(IntegrationResourceStatus.DISABLED)
                .build();
        given(member.getProject()).willReturn(project);
        given(project.getId()).willReturn(1L);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.NOTION))
                .willReturn(Optional.of(integration));
        given(integrationResourceRepository.findAllByProjectIntegrationIdOrderByIdAsc(10L))
                .willReturn(List.of(resource));
        given(credentialCipher.encrypt("access-token")).willReturn("encrypted-access-token");
        given(credentialCipher.encrypt("refresh-token")).willReturn("encrypted-refresh-token");

        ProjectIntegration result = service.connect(
                member,
                LinkType.NOTION,
                IntegrationCredentialType.OAUTH,
                "workspace-1",
                "Plog workspace",
                "bot-1",
                "access-token",
                "refresh-token",
                null
        );

        assertThat(result).isSameAs(integration);
        assertThat(result.isConnected()).isTrue();
        assertThat(result.getConnectionStatus()).isEqualTo(IntegrationConnectionStatus.ACTIVE);
        assertThat(result.getProviderConnectionId()).isEqualTo("bot-1");
        assertThat(result.getAccessTokenEncrypted()).isEqualTo("encrypted-access-token");
        assertThat(resource.getResourceStatus()).isEqualTo(IntegrationResourceStatus.ACTIVE);
        verify(projectIntegrationRepository, never()).saveAndFlush(any(ProjectIntegration.class));
    }

    @ParameterizedTest
    @EnumSource(LinkType.class)
    void disconnectsEveryProviderInsidePlogWithoutDeletingIntegrationHistory(LinkType linkType) {
        ProjectIntegrationService service = service();
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(10L)
                .externalAccountId("ext-account-id")
                .providerConnectionId("installation-1")
                .accessTokenEncrypted("encrypted-access-token")
                .refreshTokenEncrypted("encrypted-refresh-token")
                .connectionStatus(IntegrationConnectionStatus.ACTIVE)
                .build();
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, linkType))
                .willReturn(Optional.of(integration));

        service.disconnect(1L, linkType);

        if (linkType == LinkType.NOTION) {
            verify(notionWebhookEventRepository).deleteAllByWorkspaceId("ext-account-id");
        } else {
            verify(notionWebhookEventRepository, never()).deleteAllByWorkspaceId(any());
        }
        verify(integrationActivityRepository).deleteAllByIntegrationResourceProjectIntegrationId(10L);
        verify(integrationResourceRepository).deleteAllByProjectIntegrationId(10L);
        verify(integrationCollectionRunRepository).deleteByProjectId(1L);
        verify(projectIntegrationRepository).delete(integration);
    }

    @Test
    void disconnectsIntegrationThatRequiresReauthorization() {
        ProjectIntegrationService service = service();
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(10L)
                .providerConnectionId("provider-connection")
                .connectionStatus(IntegrationConnectionStatus.REAUTH_REQUIRED)
                .build();
        given(projectIntegrationRepository.findByProjectIdAndLinkTypeForUpdate(1L, LinkType.GOOGLE_DOCS))
                .willReturn(Optional.of(integration));

        service.disconnect(1L, LinkType.GOOGLE_DOCS);

        verify(integrationActivityRepository).deleteAllByIntegrationResourceProjectIntegrationId(10L);
        verify(integrationResourceRepository).deleteAllByProjectIntegrationId(10L);
        verify(integrationCollectionRunRepository).deleteByProjectId(1L);
        verify(projectIntegrationRepository).delete(integration);
    }

    @Test
    void doesNotRestoreOAuthTokensAfterIntegrationWasDisconnected() {
        ProjectIntegrationService service = service();
        ProjectIntegration integration = ProjectIntegration.builder()
                .providerConnectionId("provider-connection")
                .connectionStatus(IntegrationConnectionStatus.REVOKED)
                .build();
        given(projectIntegrationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(integration));

        service.rotateOAuthTokens(10L, "new-access-token", "new-refresh-token", null);

        assertThat(integration.getAccessTokenEncrypted()).isNull();
        assertThat(integration.getRefreshTokenEncrypted()).isNull();
        verify(credentialCipher, never()).encrypt("new-access-token");
        verify(credentialCipher, never()).encrypt("new-refresh-token");
    }

    private ProjectIntegrationService service() {
        return new ProjectIntegrationService(
                projectIntegrationRepository,
                integrationActivityRepository,
                notionWebhookEventRepository,
                integrationCollectionRunRepository,
                integrationResourceRepository,
                credentialCipher,
                projectRepository
        );
    }
}