package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
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
    private SelfFeedbackRepository selfFeedbackRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @InjectMocks
    private ProjectStatusService projectStatusService;

    @Test
    void checkAndUpdateStatusCompletesProjectWhenAllMembersSubmitted() {
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
    }

    @Test
    void checkAndUpdateStatusCompletesProjectWhenTimeoutReached() {
        Project project = projectEndedDaysAgo(7);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(1L);
        when(selfFeedbackRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(1L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(response.isPublished()).isTrue();
        assertThat(response.isTimeoutApplied()).isTrue();
        verify(projectRepository).saveAndFlush(project);
    }

    @Test
    void checkAndUpdateStatusKeepsInProgressBeforeAllSubmittedAndTimeout() {
        Project project = projectEndedDaysAgo(1);
        mockProject(project);
        when(projectMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE)).thenReturn(3L);
        when(peerEvaluationRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(5L);
        when(selfFeedbackRepository.countSubmittedByActiveProjectMembers(PROJECT_ID)).thenReturn(2L);

        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(PROJECT_ID, USER_ID, null);

        assertThat(response.currentStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.isPublished()).isFalse();
        assertThat(response.isTimeoutApplied()).isFalse();
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
