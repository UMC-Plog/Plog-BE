package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.project.dto.response.ProjectListResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.domain.task.repository.TaskRepository.ProjectTaskProgress;
import com.plog.domain.user.entity.User;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ProjectListServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private TaskRepository taskRepository;

    private ProjectListService projectListService;

    @BeforeEach
    void setUp() {
        projectListService = new ProjectListService(projectMemberRepository, taskRepository);
    }

    @Test
    void returnsProjectCardsWithMemberAndTaskSummaries() {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(10L);
        given(project.getProjectName()).willReturn("Plog");
        given(project.getProjectType()).willReturn(ProjectType.DEVELOP);
        given(project.getStatus()).willReturn(ProjectStatus.IN_PROGRESS);
        given(project.getEndDay()).willReturn(LocalDate.now().plusDays(5));

        ProjectMember myMembership = member(project, user(1L, "vana"));
        ProjectMember fourthMember = mock(ProjectMember.class);
        given(fourthMember.getProject()).willReturn(project);
        List<ProjectMember> members = List.of(
                myMembership,
                member(project, user(2L, "member2")),
                member(project, user(3L, "member3")),
                fourthMember
        );
        ProjectTaskProgress progress = mock(ProjectTaskProgress.class);
        given(progress.getProjectId()).willReturn(10L);
        given(progress.getTotalCount()).willReturn(3L);
        given(progress.getDoneCount()).willReturn(2L);

        given(projectMemberRepository.findProjectPage(
                1L,
                MemberStatus.ACTIVE,
                ProjectStatus.IN_PROGRESS,
                PageRequest.of(0, 20)
        )).willReturn(new PageImpl<>(List.of(myMembership), PageRequest.of(0, 20), 1));
        given(projectMemberRepository.findActiveMembers(List.of(10L), MemberStatus.ACTIVE))
                .willReturn(members);
        given(taskRepository.findProgressByProjectIds(List.of(10L), TaskStatus.DONE))
                .willReturn(List.of(progress));

        ProjectListResponse response = projectListService.getProjects(
                1L,
                ProjectStatus.IN_PROGRESS,
                0,
                20
        );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().remainingDays()).isEqualTo(5);
        assertThat(response.content().getFirst().memberCount()).isEqualTo(4);
        assertThat(response.content().getFirst().memberPreviews()).hasSize(3);
        assertThat(response.content().getFirst().extraMemberCount()).isEqualTo(1);
        assertThat(response.content().getFirst().progressPercent()).isEqualTo(66);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void rejectsANullPrincipal() {
        assertThatThrownBy(() -> projectListService.getProjects(null, null, 0, 20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));

        verifyNoInteractions(projectMemberRepository, taskRepository);
    }

    private ProjectMember member(Project project, User user) {
        ProjectMember member = mock(ProjectMember.class);
        given(member.getProject()).willReturn(project);
        given(member.getUser()).willReturn(user);
        return member;
    }

    private User user(Long userId, String nickname) {
        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(user.getNickname()).willReturn(nickname);
        return user;
    }
}
