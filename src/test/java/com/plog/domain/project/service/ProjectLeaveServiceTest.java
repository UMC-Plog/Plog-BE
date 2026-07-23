package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectLeaveServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectPurgeService projectPurgeService;

    private ProjectLeaveService projectLeaveService;

    @BeforeEach
    void setUp() {
        projectLeaveService = new ProjectLeaveService(
                projectRepository, projectMemberRepository, projectPurgeService);
    }

    @Test
    void ownerCanLeaveWhileOtherMembersRemain() {
        Project project = project();
        ProjectMember owner = owner(project);
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatusForUpdate(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));
        when(projectMemberRepository.countByProjectIdAndStatus(1L, MemberStatus.ACTIVE)).thenReturn(1L);

        var response = projectLeaveService.leave(1L, 7L);

        assertThat(response.success()).isTrue();
        assertThat(owner.getStatus()).isEqualTo(MemberStatus.EXIT);
        verify(projectMemberRepository).saveAndFlush(owner);
        verify(projectPurgeService, never()).purge(1L);
        verify(projectRepository, never()).delete(project);
    }

    @Test
    void permanentlyDeletesProjectWhenLastMemberLeaves() {
        Project project = project();
        ProjectMember owner = owner(project);
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatusForUpdate(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));
        when(projectMemberRepository.countByProjectIdAndStatus(1L, MemberStatus.ACTIVE)).thenReturn(0L);

        projectLeaveService.leave(1L, 7L);

        verify(projectPurgeService).purge(1L);
        verify(projectRepository).delete(project);
    }

    private Project project() {
        return Project.builder()
                .id(1L)
                .projectName("Plog")
                .inviteTokenHash("invite-token-hash")
                .inviteTokenEncrypted("invite-token-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 1, 1))
                .endDay(LocalDate.of(2026, 12, 31))
                .build();
    }

    private ProjectMember owner(Project project) {
        return ProjectMember.builder()
                .id(10L)
                .project(project)
                .role(ProjectRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .build();
    }
}
