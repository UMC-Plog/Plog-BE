package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.project.dto.request.ProjectJoinRequest;
import com.plog.domain.project.dto.response.ProjectJoinResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProjectJoinServiceTest {

    private static final String INVITE_CODE = "valid-invite-code";

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private InviteTokenService inviteTokenService;

    private ProjectJoinService projectJoinService;

    @BeforeEach
    void setUp() {
        projectJoinService = new ProjectJoinService(
                userRepository,
                projectMemberRepository,
                inviteTokenService
        );
    }

    @Test
    void createsAnActiveMemberForAValidInviteCode() {
        User user = user();
        Project project = project();
        LocalDateTime joinedAt = LocalDateTime.of(2026, 7, 20, 21, 0);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(inviteTokenService.findProjectByRawToken(INVITE_CODE)).willReturn(Optional.of(project));
        given(projectMemberRepository.findByProjectIdAndUserId(10L, 1L)).willReturn(Optional.empty());
        given(projectMemberRepository.saveAndFlush(any(ProjectMember.class))).willAnswer(invocation -> {
            ProjectMember member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 25L);
            ReflectionTestUtils.setField(member, "createdAt", joinedAt);
            ReflectionTestUtils.setField(member, "updatedAt", joinedAt);
            return member;
        });

        ProjectJoinResponse response = projectJoinService.join(
                1L,
                new ProjectJoinRequest(INVITE_CODE)
        );

        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.projectName()).isEqualTo("Plog API");
        assertThat(response.projectMemberId()).isEqualTo(25L);
        assertThat(response.role()).isEqualTo(ProjectRole.MEMBER);
        assertThat(response.projectStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.joinedAt()).isEqualTo(joinedAt);

        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        verify(projectMemberRepository).saveAndFlush(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getUser()).isSameAs(user);
        assertThat(memberCaptor.getValue().getProject()).isSameAs(project);
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(memberCaptor.getValue().getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void reactivatesTheExistingExitedMembership() {
        User user = user();
        Project project = project();
        LocalDateTime rejoinedAt = LocalDateTime.of(2026, 7, 20, 22, 0);
        ProjectMember exitedMember = ProjectMember.builder()
                .id(25L)
                .user(user)
                .project(project)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.EXIT)
                .build();
        ReflectionTestUtils.setField(exitedMember, "createdAt", rejoinedAt.minusDays(1));
        ReflectionTestUtils.setField(exitedMember, "updatedAt", rejoinedAt);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(inviteTokenService.findProjectByRawToken(INVITE_CODE)).willReturn(Optional.of(project));
        given(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .willReturn(Optional.of(exitedMember));
        given(projectMemberRepository.saveAndFlush(exitedMember)).willReturn(exitedMember);

        ProjectJoinResponse response = projectJoinService.join(
                1L,
                new ProjectJoinRequest(INVITE_CODE)
        );

        assertThat(response.projectMemberId()).isEqualTo(25L);
        assertThat(response.role()).isEqualTo(ProjectRole.MEMBER);
        assertThat(response.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.joinedAt()).isEqualTo(rejoinedAt);
        verify(projectMemberRepository).saveAndFlush(exitedMember);
    }

    @Test
    void rejectsAnAlreadyActiveMembership() {
        User user = user();
        Project project = project();
        ProjectMember activeMember = ProjectMember.builder()
                .id(25L)
                .user(user)
                .project(project)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(inviteTokenService.findProjectByRawToken(INVITE_CODE)).willReturn(Optional.of(project));
        given(projectMemberRepository.findByProjectIdAndUserId(10L, 1L))
                .willReturn(Optional.of(activeMember));

        assertProjectError(
                () -> projectJoinService.join(1L, new ProjectJoinRequest(INVITE_CODE)),
                ProjectErrorCode.PROJECT_ALREADY_JOINED
        );

        verify(projectMemberRepository, never()).saveAndFlush(any(ProjectMember.class));
    }

    @Test
    void rejectsAnInvalidInviteCode() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(inviteTokenService.findProjectByRawToken("invalid-code")).willReturn(Optional.empty());

        assertProjectError(
                () -> projectJoinService.join(1L, new ProjectJoinRequest("invalid-code")),
                ProjectErrorCode.INVALID_INVITE_CODE
        );

        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    void convertsAConcurrentMembershipUniqueViolationToAnAlreadyJoinedConflict() {
        User user = user();
        Project project = project();
        DataIntegrityViolationException duplicateMembership = constraintViolation("uk_project_member");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(inviteTokenService.findProjectByRawToken(INVITE_CODE)).willReturn(Optional.of(project));
        given(projectMemberRepository.findByProjectIdAndUserId(10L, 1L)).willReturn(Optional.empty());
        given(projectMemberRepository.saveAndFlush(any(ProjectMember.class)))
                .willThrow(duplicateMembership);

        assertProjectError(
                () -> projectJoinService.join(1L, new ProjectJoinRequest(INVITE_CODE)),
                ProjectErrorCode.PROJECT_ALREADY_JOINED
        );
    }

    @Test
    void doesNotHideAnUnrelatedDatabaseConstraintFailure() {
        User user = user();
        Project project = project();
        DataIntegrityViolationException unrelatedFailure = constraintViolation("fk_project_member_user");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(inviteTokenService.findProjectByRawToken(INVITE_CODE)).willReturn(Optional.of(project));
        given(projectMemberRepository.findByProjectIdAndUserId(10L, 1L)).willReturn(Optional.empty());
        given(projectMemberRepository.saveAndFlush(any(ProjectMember.class)))
                .willThrow(unrelatedFailure);

        assertThatThrownBy(() -> projectJoinService.join(1L, new ProjectJoinRequest(INVITE_CODE)))
                .isSameAs(unrelatedFailure);
    }

    @Test
    void rejectsANullPrincipal() {
        assertThatThrownBy(() -> projectJoinService.join(null, new ProjectJoinRequest(INVITE_CODE)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));

        verifyNoInteractions(userRepository, projectMemberRepository, inviteTokenService);
    }

    private void assertProjectError(Runnable operation, ProjectErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private User user() {
        User user = User.createLocal("member@plog.test", "encoded-password", "Member", "member");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException hibernateException = mock(ConstraintViolationException.class);
        lenient().when(hibernateException.getConstraintName()).thenReturn(constraintName);
        return new DataIntegrityViolationException("constraint violation", hibernateException);
    }

    private Project project() {
        return Project.builder()
                .id(10L)
                .projectName("Plog API")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted-invite")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 7, 20))
                .endDay(LocalDate.of(2026, 8, 20))
                .build();
    }
}
