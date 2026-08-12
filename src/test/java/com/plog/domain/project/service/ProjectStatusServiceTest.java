package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.PeerEvaluationStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.report.service.ReportLifecycleService;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private static final Long MEMBER_ID = 100L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private PeerEvaluationRepository peerEvaluationRepository;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ReportLifecycleService reportLifecycleService;
    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ProjectStatusService projectStatusService;

    @Test
    void rejectsFinalSubmissionBeforeCurrentMemberCompletesPeerEvaluations() {
        Project project = projectEndedDaysAgo(1);
        ProjectMember currentMember = activeMember(project);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatusForUpdate(
                PROJECT_ID, USER_ID, MemberStatus.ACTIVE)).thenReturn(Optional.of(currentMember));
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveEvaluator(PROJECT_ID, MEMBER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> projectStatusService.checkAndUpdateStatus(
                PROJECT_ID, USER_ID, new ProjectStatusDto.Request(ProjectStatus.COMPLETED)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EvaluationErrorCode.PEER_EVALUATION_REQUIRED_FOR_FINAL_SUBMISSION));

        assertThat(currentMember.isFinalSubmitted()).isFalse();
        verifyNoInteractions(reportLifecycleService);
    }

    @Test
    void finalSubmissionWaitsForOtherActiveMembers() {
        Project project = projectEndedDaysAgo(1);
        ProjectMember currentMember = activeMember(project);
        mockProjectAndCurrentMember(project, currentMember);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveEvaluator(PROJECT_ID, MEMBER_ID)).thenReturn(2L);
        when(projectMemberRepository.countByProjectIdAndStatusAndFinalSubmittedAtIsNotNull(
                PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(1L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(
                PROJECT_ID, USER_ID, new ProjectStatusDto.Request(ProjectStatus.COMPLETED));

        assertThat(currentMember.isFinalSubmitted()).isTrue();
        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.isCurrentMemberFinalSubmitted()).isTrue();
        assertThat(response.completedFinalSubmissionCount()).isEqualTo(1);
        assertThat(response.totalFinalSubmissionCount()).isEqualTo(3);
        assertThat(response.isPublished()).isFalse();
        verifyNoInteractions(reportLifecycleService);
    }

    @Test
    void completesProjectAndStartsReportWhenAllActiveMembersFinalSubmitted() {
        Project project = projectEndedDaysAgo(1);
        ProjectMember currentMember = activeMember(project);
        mockProjectAndCurrentMember(project, currentMember);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveEvaluator(PROJECT_ID, MEMBER_ID)).thenReturn(2L);
        when(projectMemberRepository.countByProjectIdAndStatusAndFinalSubmittedAtIsNotNull(
                PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(response.isPublished()).isTrue();
        assertThat(response.isTimeoutApplied()).isFalse();
        verify(projectRepository).saveAndFlush(project);
        verify(reportLifecycleService).startFor(project);
    }

    @Test
    void duplicateFinalSubmissionIsIdempotent() {
        Project project = projectEndedDaysAgo(1);
        ProjectMember currentMember = activeMember(project, TimeUtil.now().minusMinutes(1));
        mockProjectAndCurrentMember(project, currentMember);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(2L);
        when(projectMemberRepository.countByProjectIdAndStatusAndFinalSubmittedAtIsNotNull(
                PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(1L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.isCurrentMemberFinalSubmitted()).isTrue();
        verifyNoInteractions(peerEvaluationRepository);
        verifyNoInteractions(reportLifecycleService);
    }

    @Test
    void timeoutStillCompletesProjectWithoutAllFinalSubmissions() {
        Project project = projectEndedDaysAgo(7);
        ProjectMember currentMember = activeMember(project);
        mockProjectAndCurrentMember(project, currentMember);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveEvaluator(PROJECT_ID, MEMBER_ID)).thenReturn(0L);
        when(projectMemberRepository.countByProjectIdAndStatusAndFinalSubmittedAtIsNotNull(
                PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(0L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(response.isTimeoutApplied()).isTrue();
        assertThat(currentMember.isFinalSubmitted()).isFalse();
        verify(projectRepository).saveAndFlush(project);
        verify(reportLifecycleService).startFor(project);
    }

    private void mockProjectAndCurrentMember(Project project, ProjectMember currentMember) {
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatusForUpdate(
                PROJECT_ID, USER_ID, MemberStatus.ACTIVE)).thenReturn(Optional.of(currentMember));
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(currentMember));
        when(reportRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
    }

    private ProjectMember activeMember(Project project) {
        return activeMember(project, null);
    }

    private ProjectMember activeMember(Project project, LocalDateTime finalSubmittedAt) {
        return ProjectMember.builder()
                .id(MEMBER_ID)
                .project(project)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .finalSubmittedAt(finalSubmittedAt)
                .build();
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
