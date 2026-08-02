package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.dto.response.IntegrationStatusResponse;
import com.plog.domain.integration.entity.IntegrationConnectionStatus;
import com.plog.domain.integration.entity.IntegrationCollectionStatus;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationCollectionRunRepository;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;

    @Mock
    private IntegrationResourceRepository integrationResourceRepository;

    @Mock
    private IntegrationCollectionRunRepository integrationCollectionRunRepository;

    @Mock
    private ProjectIntegrationService projectIntegrationService;

    @InjectMocks
    private IntegrationService integrationService;

    @Test
    @DisplayName("활성 프로젝트 멤버의 외부 툴 연동 상태를 LinkType 순서로 조회한다")
    void getProjectIntegrationsReturnsStatusesInLinkTypeOrder() {
        Long projectId = 1L;
        Long userId = 10L;
        ProjectMember projectMember = projectMember();
        ProjectIntegration github = projectIntegration(LinkType.GITHUB, "github-user");
        ProjectIntegration notion = projectIntegration(LinkType.NOTION, "notion-user");

        given(projectRepository.existsById(projectId)).willReturn(true);
        given(projectAccessService.requireActiveMember(projectId, userId)).willReturn(projectMember);
        given(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(projectId))
                .willReturn(List.of(notion, github));

        IntegrationStatusResponse response = integrationService.getProjectIntegrations(projectId, userId);

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.projectMemberId()).isEqualTo(100L);
        assertThat(response.integrations()).extracting("linkType")
                .containsExactly(LinkType.GITHUB, LinkType.FIGMA, LinkType.NOTION, LinkType.GOOGLE);
        assertThat(response.integrations()).extracting("linked")
                .containsExactly(true, false, true, false);
        assertThat(response.integrations()).extracting("connectedAccountName")
                .containsExactly("github-user", null, "notion-user", null);
        verify(projectIntegrationRepository).findAllByProjectIdOrderByLinkTypeAsc(projectId);
    }

    @Test
    @DisplayName("프로젝트가 없으면 PROJECT_NOT_FOUND를 던지고 권한과 연결을 조회하지 않는다")
    void getProjectIntegrationsThrowsProjectNotFound() {
        Long projectId = 404L;
        Long userId = 10L;
        given(projectRepository.existsById(projectId)).willReturn(false);

        assertThatThrownBy(() -> integrationService.getProjectIntegrations(projectId, userId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND)
                );

        verify(projectAccessService, never()).requireActiveMember(projectId, userId);
        verify(projectIntegrationRepository, never()).findAllByProjectIdOrderByLinkTypeAsc(anyLong());
    }

    @Test
    @DisplayName("활성 프로젝트 멤버가 아니면 PROJECT_MEMBER_REQUIRED를 전파한다")
    void getProjectIntegrationsRequiresActiveProjectMember() {
        Long projectId = 1L;
        Long userId = 10L;
        given(projectRepository.existsById(projectId)).willReturn(true);
        given(projectAccessService.requireActiveMember(projectId, userId))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        assertThatThrownBy(() -> integrationService.getProjectIntegrations(projectId, userId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_MEMBER_REQUIRED)
                );

        verify(projectIntegrationRepository, never()).findAllByProjectIdOrderByLinkTypeAsc(anyLong());
    }

    @Test
    @DisplayName("완료된 프로젝트는 외부 워크스페이스 연동 해제를 거부한다")
    void disconnectRejectsCompletedProject() {
        Long projectId = 1L;
        Long userId = 10L;
        Project completedProject = mock(Project.class);
        given(projectRepository.findById(projectId)).willReturn(Optional.of(completedProject));
        given(completedProject.isCompleted()).willReturn(true);

        assertThatThrownBy(() -> integrationService.disconnect(projectId, userId, LinkType.GITHUB))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(IntegrationErrorCode.WORKSPACE_INTEGRATION_LOCKED));

        verify(projectAccessService).requireActiveMember(projectId, userId);
        verify(projectIntegrationService, never()).disconnect(projectId, LinkType.GITHUB);
    }

    @Test
    @DisplayName("외부 연동 해제는 연결 행을 삭제하지 않고 내부 연결 상태를 변경한다")
    void disconnectUsesSoftDisconnection() {
        Long projectId = 1L;
        Long userId = 10L;
        Project project = mock(Project.class);
        given(projectRepository.findById(projectId)).willReturn(Optional.of(project));

        integrationService.disconnect(projectId, userId, LinkType.GITHUB);

        verify(projectAccessService).requireActiveMember(projectId, userId);
        verify(projectIntegrationService).disconnect(projectId, LinkType.GITHUB);
        verify(projectIntegrationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("연결 row가 있어도 externalAccountId가 null이면 미연결로 응답한다")
    void getProjectIntegrationsTreatsNullExternalAccountIdAsUnlinked() {
        Long projectId = 1L;
        Long userId = 10L;
        ProjectMember projectMember = projectMember();
        ProjectIntegration figma = projectIntegration(LinkType.FIGMA, null);
        given(projectRepository.existsById(projectId)).willReturn(true);
        given(projectAccessService.requireActiveMember(projectId, userId)).willReturn(projectMember);
        given(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(projectId)).willReturn(List.of(figma));

        IntegrationStatusResponse response = integrationService.getProjectIntegrations(projectId, userId);

        assertThat(response.integrations()).extracting("linkType")
                .containsExactly(LinkType.GITHUB, LinkType.FIGMA, LinkType.NOTION, LinkType.GOOGLE);
        assertThat(response.integrations()).extracting("linked")
                .containsExactly(false, false, false, false);
        assertThat(response.integrations()).extracting("connectedAccountName")
                .containsExactly(null, null, null, null);
        verify(projectIntegrationRepository).findAllByProjectIdOrderByLinkTypeAsc(projectId);
    }

    @Test
    @DisplayName("Plog에서 해제한 외부 계정은 provider 식별자가 남아 있어도 미연결로 응답한다")
    void getProjectIntegrationsTreatsDisconnectedIntegrationAsUnlinked() {
        Long projectId = 1L;
        Long userId = 10L;
        ProjectMember projectMember = projectMember();
        ProjectIntegration github = ProjectIntegration.builder()
                .linkType(LinkType.GITHUB)
                .credentialType(IntegrationCredentialType.APP_INSTALLATION)
                .externalAccountId("UMC-Plog")
                .externalAccountName("UMC-Plog")
                .providerConnectionId("12345")
                .connectionStatus(IntegrationConnectionStatus.REVOKED)
                .build();
        given(projectRepository.existsById(projectId)).willReturn(true);
        given(projectAccessService.requireActiveMember(projectId, userId)).willReturn(projectMember);
        given(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(projectId))
                .willReturn(List.of(github));

        IntegrationStatusResponse response = integrationService.getProjectIntegrations(projectId, userId);

        assertThat(response.integrations().getFirst().linked()).isFalse();
        assertThat(response.integrations().getFirst().connectedAccountName()).isNull();
    }

    @Test
    @DisplayName("재연동 필요 여부와 provider 리소스의 최근 수집 상태를 함께 반환한다")
    void getProjectIntegrationsReturnsCollectionAndReauthorizationStatus() {
        Long projectId = 1L;
        Long userId = 10L;
        ProjectMember projectMember = projectMember();
        ProjectIntegration notion = ProjectIntegration.builder()
                .id(30L)
                .linkType(LinkType.NOTION)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("workspace")
                .externalAccountName("Plog Workspace")
                .providerConnectionId("bot")
                .connectionStatus(IntegrationConnectionStatus.REAUTH_REQUIRED)
                .build();
        IntegrationResource resource = IntegrationResource.builder()
                .id(300L)
                .projectIntegration(notion)
                .resourceType(IntegrationResourceType.NOTION_PAGE)
                .providerResourceId("page")
                .resourceName("회의록")
                .resourceStatus(IntegrationResourceStatus.REAUTH_REQUIRED)
                .collectionStatus(IntegrationCollectionStatus.REAUTH_REQUIRED)
                .lastCollectionFailure("provider reauthorization required")
                .build();
        given(projectRepository.existsById(projectId)).willReturn(true);
        given(projectAccessService.requireActiveMember(projectId, userId)).willReturn(projectMember);
        given(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(projectId))
                .willReturn(List.of(notion));
        given(integrationResourceRepository.findAllByProjectIntegrationProjectIdOrderByIdAsc(projectId))
                .willReturn(List.of(resource));

        IntegrationStatusResponse response = integrationService.getProjectIntegrations(projectId, userId);

        assertThat(response.integrations().get(2).linked()).isFalse();
        assertThat(response.integrations().get(2).connectedAccountName()).isEqualTo("Plog Workspace");
        assertThat(response.integrations().get(2).connectionStatus())
                .isEqualTo(IntegrationConnectionStatus.REAUTH_REQUIRED);
        assertThat(response.integrations().get(2).reauthorizationRequired()).isTrue();
        assertThat(response.integrations().get(2).collectionStatus())
                .isEqualTo(IntegrationCollectionStatus.REAUTH_REQUIRED);
        assertThat(response.integrations().get(2).lastCollectionFailure())
                .isEqualTo("provider reauthorization required");
    }

    private ProjectMember projectMember() {
        return ProjectMember.builder()
                .id(100L)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
    }

    private ProjectIntegration projectIntegration(
            LinkType linkType,
            String externalAccountId
    ) {
        return ProjectIntegration.builder()
                .id((long) linkType.ordinal() + 1)
                .linkType(linkType)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId(externalAccountId)
                .externalAccountName(externalAccountId)
                .providerConnectionId(externalAccountId)
                .build();
    }
}
