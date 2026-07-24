package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.dto.response.ExternalLinkStatusResponse;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExternalLinkServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;

    @InjectMocks
    private ExternalLinkService externalLinkService;

    @Test
    @DisplayName("활성 프로젝트 멤버의 외부 툴 연동 상태를 LinkType 순서로 조회한다")
    void getMyExternalLinksReturnsStatusesInLinkTypeOrder() {
        Long projectId = 1L;
        Long userId = 10L;
        ProjectMember projectMember = projectMember();
        ProjectIntegration github = projectIntegration(LinkType.GITHUB, "github-user");
        ProjectIntegration notion = projectIntegration(LinkType.NOTION, "notion-user");

        given(projectRepository.existsById(projectId)).willReturn(true);
        given(projectAccessService.requireActiveMember(projectId, userId)).willReturn(projectMember);
        given(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(projectId))
                .willReturn(List.of(notion, github));

        ExternalLinkStatusResponse response = externalLinkService.getMyExternalLinks(projectId, userId);

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.projectMemberId()).isEqualTo(100L);
        assertThat(response.links()).extracting("linkType")
                .containsExactly(LinkType.GITHUB, LinkType.FIGMA, LinkType.NOTION, LinkType.GOOGLE);
        assertThat(response.links()).extracting("linked")
                .containsExactly(true, false, true, false);
        assertThat(response.links()).extracting("connectedAccountName")
                .containsExactly("github-user", null, "notion-user", null);
        verify(projectIntegrationRepository).findAllByProjectIdOrderByLinkTypeAsc(projectId);
    }

    @Test
    @DisplayName("프로젝트가 없으면 PROJECT_NOT_FOUND를 던지고 권한과 연결을 조회하지 않는다")
    void getMyExternalLinksThrowsProjectNotFound() {
        Long projectId = 404L;
        Long userId = 10L;
        given(projectRepository.existsById(projectId)).willReturn(false);

        assertThatThrownBy(() -> externalLinkService.getMyExternalLinks(projectId, userId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND)
                );

        verify(projectAccessService, never()).requireActiveMember(projectId, userId);
        verify(projectIntegrationRepository, never()).findAllByProjectIdOrderByLinkTypeAsc(anyLong());
    }

    @Test
    @DisplayName("활성 프로젝트 멤버가 아니면 PROJECT_MEMBER_REQUIRED를 전파한다")
    void getMyExternalLinksRequiresActiveProjectMember() {
        Long projectId = 1L;
        Long userId = 10L;
        given(projectRepository.existsById(projectId)).willReturn(true);
        given(projectAccessService.requireActiveMember(projectId, userId))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        assertThatThrownBy(() -> externalLinkService.getMyExternalLinks(projectId, userId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_MEMBER_REQUIRED)
                );

        verify(projectIntegrationRepository, never()).findAllByProjectIdOrderByLinkTypeAsc(anyLong());
    }

    @Test
    @DisplayName("연결 row가 있어도 externalAccountId가 null이면 미연결로 응답한다")
    void getMyExternalLinksTreatsNullExternalAccountIdAsUnlinked() {
        Long projectId = 1L;
        Long userId = 10L;
        ProjectMember projectMember = projectMember();
        ProjectIntegration figma = projectIntegration(LinkType.FIGMA, null);
        given(projectRepository.existsById(projectId)).willReturn(true);
        given(projectAccessService.requireActiveMember(projectId, userId)).willReturn(projectMember);
        given(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(projectId)).willReturn(List.of(figma));

        ExternalLinkStatusResponse response = externalLinkService.getMyExternalLinks(projectId, userId);

        assertThat(response.links()).extracting("linkType")
                .containsExactly(LinkType.GITHUB, LinkType.FIGMA, LinkType.NOTION, LinkType.GOOGLE);
        assertThat(response.links()).extracting("linked")
                .containsExactly(false, false, false, false);
        assertThat(response.links()).extracting("connectedAccountName")
                .containsExactly(null, null, null, null);
        verify(projectIntegrationRepository).findAllByProjectIdOrderByLinkTypeAsc(projectId);
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
                .linkType(linkType)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId(externalAccountId)
                .externalAccountName(externalAccountId)
                .providerConnectionId(externalAccountId)
                .build();
    }
}
