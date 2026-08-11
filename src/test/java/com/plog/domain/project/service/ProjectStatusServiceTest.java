package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.plog.domain.integration.service.IntegrationActorMappingStatusService;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.PeerEvaluationStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.report.service.ReportLifecycleService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
    private SelfFeedbackRepository selfFeedbackRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private IntegrationActorMappingStatusService actorMappingStatusService;

    @Mock
    private ReportLifecycleService reportLifecycleService;

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ProjectStatusService projectStatusService;

    @BeforeEach
    void allowCompletedMappingsByDefault() {
        lenient().when(actorMappingStatusService
                        .areAllActiveMemberMappingsCompleted(anyLong(), anyLong()))
                .thenReturn(true);
    }

    @Test
    void checkAndUpdateStatusCompletesProjectWhenAllEvaluationsAndSelfFeedbacksSubmitted() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(6L);
        when(selfFeedbackRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(3L);

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
    void checkAndUpdateStatusDelegatesReportStartToLifecycleService() {
        Project project = projectEndedDaysAgo(1);
        Report report = org.mockito.Mockito.mock(Report.class);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(2L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(2L);
        when(selfFeedbackRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(2L);
        when(reportLifecycleService.startFor(project)).thenReturn(Optional.of(report));

        projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        verify(reportLifecycleService).startFor(project);
    }

    @Test
    void completeAndStartReportAutomaticallyCompletesAfterTheLastSubmission() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(2L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(2L);
        when(selfFeedbackRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(2L);

        projectStatusService.completeAndStartReportIfAllEvaluationsSubmitted(PROJECT_ID);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        verify(projectRepository).saveAndFlush(project);
        verify(reportLifecycleService).startFor(project);
    }

    @Test
    void checkAndUpdateStatusKeepsInProgressWhenSelfFeedbackIsMissing() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(6L);
        when(selfFeedbackRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(2L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.isPublished()).isFalse();
        verifyNoInteractions(reportLifecycleService);
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
        verifyNoInteractions(actorMappingStatusService);
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
        verifyNoInteractions(actorMappingStatusService);
    }

    @Test
    void checkAndUpdateStatusCompletesProjectWhenAllActiveMembersMappedTheirAccounts() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(6L);
        when(selfFeedbackRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(3L);

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
    void checkAndUpdateStatusRejectsCompletionWhenAnActiveMemberHasNotMappedAnAccount() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(6L);
        when(selfFeedbackRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(3L);

        when(actorMappingStatusService.areAllActiveMemberMappingsCompleted(PROJECT_ID, 3L))
                .thenReturn(false);

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
    void checkAndUpdateStatusRejectsUnsupportedRequestedStatus() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);

        assertThatThrownBy(() -> projectStatusService.checkAndUpdateStatus(
                PROJECT_ID,
                USER_ID,
                new ProjectStatusDto.Request(ProjectStatus.IN_PROGRESS)
        )).isInstanceOf(ApiException.class);
    }

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
    void checkAndUpdateStatusCompletesSoloProjectAfterSelfFeedbackSubmission() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(1L);
        when(selfFeedbackRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(1L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(response.isPublished()).isTrue();
        verify(reportLifecycleService).startFor(project);
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
        LocalDate today = TimeUtil.today();
        return Project.builder()
                .id(PROJECT_ID)
                .projectName("Plog")
                .inviteTokenHash("invite-token-hash")
                .inviteTokenEncrypted("invite-token-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today.minusDays(30))
                .endDay(today.minusDays(daysAgo))
                .peerEvaluationStatus(PeerEvaluationStatus.OPEN)
                .build();
    }
}
