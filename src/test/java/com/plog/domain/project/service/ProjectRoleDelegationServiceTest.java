package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.dto.request.ProjectRoleDelegationRequest;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectRoleDelegationServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private ProjectRoleDelegationService service;

    @BeforeEach
    void setUp() {
        service = new ProjectRoleDelegationService(projectRepository, projectMemberRepository);
    }

    @Test
    void ownerDelegatesOwnershipToAnotherActiveMember() {
        Project project = project();
        ProjectMember owner = ProjectMember.builder()
                .id(10L)
                .role(ProjectRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .build();
        ProjectMember target = ProjectMember.builder()
                .id(20L)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatusForUpdate(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));
        when(projectMemberRepository.findByProjectIdAndIdAndStatusForUpdate(1L, 20L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(target));

        var response = service.delegateRole(1L, 7L, 20L, new ProjectRoleDelegationRequest(ProjectRole.OWNER));

        assertThat(response.projectId()).isEqualTo(1L);
        assertThat(response.newOwnerMemberId()).isEqualTo(20L);
        assertThat(owner.getRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(target.getRole()).isEqualTo(ProjectRole.OWNER);
        verify(projectMemberRepository).saveAndFlush(target);
    }

    @Test
    void nonOwnerCannotDelegateOwnership() {
        Project project = project();
        ProjectMember member = ProjectMember.builder()
                .id(10L)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatusForUpdate(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.delegateRole(
                1L, 7L, 20L, new ProjectRoleDelegationRequest(ProjectRole.OWNER)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED));
    }

    @Test
    void missingTargetMemberIsRejected() {
        Project project = project();
        ProjectMember owner = ProjectMember.builder()
                .id(10L)
                .role(ProjectRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .build();
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatusForUpdate(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));
        when(projectMemberRepository.findByProjectIdAndIdAndStatusForUpdate(1L, 20L, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delegateRole(
                1L, 7L, 20L, new ProjectRoleDelegationRequest(ProjectRole.OWNER)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void nonOwnerRoleRequestIsRejected() {
        assertThatThrownBy(() -> service.delegateRole(
                1L, 7L, 20L, new ProjectRoleDelegationRequest(ProjectRole.MEMBER)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ProjectErrorCode.INVALID_PROJECT_ROLE_TRANSFER));
    }

    private Project project() {
        return Project.builder()
                .id(1L)
                .projectName("Plog")
                .inviteTokenHash("hash")
                .inviteTokenEncrypted("encrypted")
                .projectType(com.plog.domain.project.entity.ProjectType.DEVELOP)
                .status(com.plog.domain.project.entity.ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 7, 1))
                .endDay(LocalDate.of(2026, 8, 1))
                .build();
    }
}
