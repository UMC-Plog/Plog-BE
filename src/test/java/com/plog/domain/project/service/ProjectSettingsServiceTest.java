package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.plog.domain.integration.entity.IntegrationCredentialType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.dto.ProjectSettingsDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.exception.ProjectApiErrorCode;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.service.IntegrationActivityReportLogAdapter;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectSettingsServiceTest {
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ProjectIntegrationRepository projectIntegrationRepository;
    @Mock private InviteTokenCipher inviteTokenCipher;
    @Mock private IntegrationActivityReportLogAdapter reportLogAdapter;

    private ProjectSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ProjectSettingsService(
                projectRepository,
                projectMemberRepository,
                projectIntegrationRepository,
                inviteTokenCipher,
                new ProjectSettingsValidator(),
                reportLogAdapter
        );
    }

    @Test
    void memberCanChangeProjectSettings() {
        Project project = project();
        ProjectMember member = ProjectMember.builder()
                .id(3L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));

        ProjectSettingsDto.UpdateResponse response = service.updateSettings(
                1L, 7L, new ProjectSettingsDto.UpdateRequest("New name", null, null));

        assertThat(response.projectName()).isEqualTo("New name");
        assertThat(project.getProjectName()).isEqualTo("New name");
    }

    @Test
    void nonMemberCannotChangeProjectSettings() {
        Project project = project();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSettings(
                1L, 7L, new ProjectSettingsDto.UpdateRequest("New name", null, null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ProjectApiErrorCode.PROJECT_MEMBER_REQUIRED));
    }

    @Test
    void ownerCanSetTodayAsExpectedEndDate() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        Project project = project();
        ProjectMember owner = ProjectMember.builder()
                .id(3L).role(ProjectRole.OWNER).status(MemberStatus.ACTIVE).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));

        ProjectSettingsDto.UpdateResponse response = service.updateSettings(
                1L, 7L, new ProjectSettingsDto.UpdateRequest(null, today, null));

        assertThat(response.endDay()).isEqualTo(today);
        assertThat(project.getEndDay()).isEqualTo(today);
    }

    @Test
    void endDayChangeReprojectsEveryExistingIntegration() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        Project project = project();
        ProjectMember owner = ProjectMember.builder()
                .id(3L).role(ProjectRole.OWNER).status(MemberStatus.ACTIVE).build();
        ProjectIntegration github = ProjectIntegration.builder()
                .id(20L)
                .project(project)
                .linkType(LinkType.GITHUB)
                .credentialType(IntegrationCredentialType.APP_INSTALLATION)
                .externalAccountId("umc-plog")
                .externalAccountName("UMC-Plog")
                .providerConnectionId("1234")
                .build();
        ProjectIntegration google = ProjectIntegration.builder()
                .id(21L)
                .project(project)
                .linkType(LinkType.GOOGLE_DOCS)
                .credentialType(IntegrationCredentialType.OAUTH)
                .externalAccountId("google")
                .externalAccountName("Google")
                .providerConnectionId("google-account")
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(1L))
                .thenReturn(List.of(github, google));

        service.updateSettings(
                1L, 7L, new ProjectSettingsDto.UpdateRequest(null, today.plusDays(7), null));

        verify(reportLogAdapter).synchronizeProjectIntegrationActivities(20L);
        verify(reportLogAdapter).synchronizeProjectIntegrationActivities(21L);
    }

    @Test
    void unchangedEndDayDoesNotReprojectIntegrations() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        Project project = project(today);
        ProjectMember owner = ProjectMember.builder()
                .id(3L).role(ProjectRole.OWNER).status(MemberStatus.ACTIVE).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));

        service.updateSettings(
                1L, 7L, new ProjectSettingsDto.UpdateRequest(null, today, null));

        verify(projectIntegrationRepository, never()).findAllByProjectIdOrderByLinkTypeAsc(1L);
        verify(reportLogAdapter, never()).synchronizeProjectIntegrationActivities(any());
    }

    @Test
    void ownerCannotSetAPastDateAsExpectedEndDate() {
        Project project = project();
        ProjectMember owner = ProjectMember.builder()
                .id(3L).role(ProjectRole.OWNER).status(MemberStatus.ACTIVE).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.updateSettings(
                1L, 7L, new ProjectSettingsDto.UpdateRequest(
                        null, LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1), null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectApiErrorCode.VALIDATION_ERROR));
    }

    @Test
    void settingsExposeProjectScopedIntegrationForEveryActiveMember() {
        Project project = project();
        ProjectMember member = ProjectMember.builder()
                .id(3L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        ProjectIntegration integration = ProjectIntegration.builder()
                .id(20L)
                .linkType(LinkType.GITHUB)
                .credentialType(IntegrationCredentialType.APP_INSTALLATION)
                .externalAccountId("umc-plog")
                .externalAccountName("UMC-Plog")
                .providerConnectionId("1234")
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(inviteTokenCipher.decrypt("encrypted")).thenReturn("invite-token");
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(1L))
                .thenReturn(List.of(integration));

        ProjectSettingsDto.Response response = service.getSettings(1L, 7L);

        assertThat(response.externalConnections())
                .containsExactly(new ProjectSettingsDto.ExternalConnection(20L, "GITHUB", true));
        verify(projectIntegrationRepository).findAllByProjectIdOrderByLinkTypeAsc(1L);
    }

    private Project project() {
        return project(LocalDate.of(2026, 8, 1));
    }

    private Project project(LocalDate endDay) {
        return Project.builder()
                .id(1L)
                .projectName("Plog")
                .inviteTokenHash("hash")
                .inviteTokenEncrypted("encrypted")
                .projectType(com.plog.domain.project.entity.ProjectType.DEVELOP)
                .status(com.plog.domain.project.entity.ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 7, 1))
                .endDay(endDay)
                .build();
    }
}
