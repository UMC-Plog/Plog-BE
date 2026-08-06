package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.service.ReportLifecycleService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectStatusServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private PeerEvaluationRepository peerEvaluationRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;

    @Mock
    private IntegrationActivityRepository integrationActivityRepository;

    @Mock
    private ReportLifecycleService reportLifecycleService;

    @InjectMocks
    private ProjectStatusService projectStatusService;

    // 셀프 피드백은 완료 조건이 아니다. 여기서 셀프 피드백을 한 건도 만들지 않는 것이 그 자체로 검증이다.
    @Test
    void checkAndUpdateStatusCompletesProjectWhenAllPeerEvaluationsSubmitted() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(6L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(
                PROJECT_ID,
                USER_ID,
                new ProjectStatusDto.Request(ProjectStatus.COMPLETED)
        );

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(response.isPublished()).isTrue();
        assertThat(response.isTimeoutApplied()).isFalse();
        verify(projectRepository).saveAndFlush(project);
        verify(reportLifecycleService).startFor(project);
    }

    @Test
    void checkAndUpdateStatusCompletesProjectWhenTimeoutReached() {
        Project project = projectEndedDaysAgo(7);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(1L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(response.isPublished()).isTrue();
        assertThat(response.isTimeoutApplied()).isTrue();
        verify(projectRepository).saveAndFlush(project);
        verify(reportLifecycleService).startFor(project);
        verifyNoInteractions(projectIntegrationRepository, integrationActivityRepository);
    }

    // 평가가 아직 안 닫혔으면 리포트도 시작되면 안 된다 — 완료 전환과 리포트 시작은 한 몸이다.
    @Test
    void checkAndUpdateStatusKeepsInProgressBeforeAllSubmittedAndTimeout() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(5L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.isPublished()).isFalse();
        assertThat(response.isTimeoutApplied()).isFalse();
        verifyNoInteractions(reportLifecycleService);
        verifyNoInteractions(projectIntegrationRepository, integrationActivityRepository);
    }

    @Test
    void checkAndUpdateStatusCompletesProjectWhenConnectedIntegrationHasNoUnassignedActors() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(6L);

        ProjectIntegration integration = org.mockito.Mockito.mock(ProjectIntegration.class);
        when(integration.isConnected()).thenReturn(true);
        when(integration.getId()).thenReturn(20L);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID))
                .thenReturn(java.util.List.of(integration));
        when(integrationActivityRepository.existsUnassignedActivityActorByProjectIntegrationId(20L))
                .thenReturn(false);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(
                PROJECT_ID,
                USER_ID,
                new ProjectStatusDto.Request(ProjectStatus.COMPLETED)
        );

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(response.isPublished()).isTrue();
        verify(projectRepository).saveAndFlush(project);
        verify(reportLifecycleService).startFor(project);
    }

    @Test
    void checkAndUpdateStatusRejectsCompletionWhenConnectedIntegrationHasUnassignedActor() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(6L);

        ProjectIntegration integration = org.mockito.Mockito.mock(ProjectIntegration.class);
        when(integration.isConnected()).thenReturn(true);
        when(integration.getId()).thenReturn(20L);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID))
                .thenReturn(java.util.List.of(integration));
        when(integrationActivityRepository.existsUnassignedActivityActorByProjectIntegrationId(20L))
                .thenReturn(true);

        assertThatThrownBy(() -> projectStatusService.checkAndUpdateStatus(
                PROJECT_ID,
                USER_ID,
                new ProjectStatusDto.Request(ProjectStatus.COMPLETED)
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ProjectErrorCode.ACTOR_MAPPING_REQUIRED);
    }

    @Test
    void checkAndUpdateStatusRejectsCompletionWhenSecondConnectedIntegrationHasUnassignedActor() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(6L);

        ProjectIntegration firstIntegration = org.mockito.Mockito.mock(ProjectIntegration.class);
        when(firstIntegration.isConnected()).thenReturn(true);
        when(firstIntegration.getId()).thenReturn(20L);
        ProjectIntegration secondIntegration = org.mockito.Mockito.mock(ProjectIntegration.class);
        when(secondIntegration.isConnected()).thenReturn(true);
        when(secondIntegration.getId()).thenReturn(21L);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID))
                .thenReturn(java.util.List.of(firstIntegration, secondIntegration));
        when(integrationActivityRepository.existsUnassignedActivityActorByProjectIntegrationId(20L))
                .thenReturn(false);
        when(integrationActivityRepository.existsUnassignedActivityActorByProjectIntegrationId(21L))
                .thenReturn(true);

        assertThatThrownBy(() -> projectStatusService.checkAndUpdateStatus(
                PROJECT_ID,
                USER_ID,
                new ProjectStatusDto.Request(ProjectStatus.COMPLETED)
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ProjectErrorCode.ACTOR_MAPPING_REQUIRED);

        verify(integrationActivityRepository).existsUnassignedActivityActorByProjectIntegrationId(20L);
        verify(integrationActivityRepository).existsUnassignedActivityActorByProjectIntegrationId(21L);
    }

    @Test
    void checkAndUpdateStatusCompletesProjectWithoutCheckingDisconnectedIntegrations() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(6L);

        ProjectIntegration disconnectedIntegration = org.mockito.Mockito.mock(ProjectIntegration.class);
        when(disconnectedIntegration.isConnected()).thenReturn(false);
        when(projectIntegrationRepository.findAllByProjectIdOrderByLinkTypeAsc(PROJECT_ID))
                .thenReturn(java.util.List.of(disconnectedIntegration));

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(
                PROJECT_ID,
                USER_ID,
                new ProjectStatusDto.Request(ProjectStatus.COMPLETED)
        );

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(response.isPublished()).isTrue();
        verifyNoInteractions(integrationActivityRepository);
    }

    @Test
    void checkAndUpdateStatusRejectsUnsupportedRequestedStatus() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);

        assertThatThrownBy(() -> projectStatusService.checkAndUpdateStatus(
                PROJECT_ID,
                USER_ID,
                new ProjectStatusDto.Request(ProjectStatus.IN_PROGRESS)
        )).isInstanceOf(ApiException.class);
    }

    // 활성 멤버가 1명이면 필요 피어 평가 건수가 1 x 0 = 0 이 된다.
    // 그대로 두면 아무 입력 없이 완료되므로, 이 경우는 타임아웃으로만 완료되어야 한다.
    @Test
    void checkAndUpdateStatusKeepsSoloProjectInProgressBeforeTimeout() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(1L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(
                PROJECT_ID,
                USER_ID,
                new ProjectStatusDto.Request(ProjectStatus.COMPLETED)
        );

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.isPublished()).isFalse();
        assertThat(response.isTimeoutApplied()).isFalse();
    }

    @Test
    void checkAndUpdateStatusCompletesSoloProjectOnTimeout() {
        Project project = projectEndedDaysAgo(7);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(1L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(response.isTimeoutApplied()).isTrue();
    }

    private void mockProject(Project project) {
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    private Project projectEndedDaysAgo(int daysAgo) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return Project.builder()
                .id(PROJECT_ID)
                .projectName("Plog")
                .inviteTokenHash("invite-token-hash")
                .inviteTokenEncrypted("invite-token-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today.minusDays(30))
                .endDay(today.minusDays(daysAgo))
                .build();
    }
}
