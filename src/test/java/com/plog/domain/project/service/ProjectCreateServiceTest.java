package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.domain.project.dto.response.ProjectCreateResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectCreateServiceTest {

    private static final String INVITE_BASE_URL = "https://plog.test/invites";
    private static final String RAW_INVITE_TOKEN = "raw-invite-token";
    private static final String INVITE_TOKEN_HASH = "invite-token-hash";
    private static final String ENCRYPTED_INVITE_TOKEN = "encrypted-invite-token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private InviteTokenService inviteTokenService;

    private ProjectCreateService projectCreateService;

    @BeforeEach
    void setUp() {
        projectCreateService = new ProjectCreateService(
                userRepository,
                projectRepository,
                projectMemberRepository,
                chatRoomRepository,
                inviteTokenService,
                INVITE_BASE_URL
        );
    }

    @ParameterizedTest
    @EnumSource(ProjectType.class)
    void createsAProjectAndItsOwnerWithAnInvite(ProjectType projectType) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate endDay = today.plusDays(30);
        User user = User.createLocal("owner@plog.test", "encoded-password", "Owner", "owner");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        executeInviteTokenPersistence();
        given(projectRepository.save(any(Project.class))).willAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            return Project.builder()
                    .id(10L)
                    .projectName(project.getProjectName())
                    .inviteTokenHash(project.getInviteTokenHash())
                    .inviteTokenEncrypted(project.getInviteTokenEncrypted())
                    .projectType(project.getProjectType())
                    .status(project.getStatus())
                    .startDay(project.getStartDay())
                    .endDay(project.getEndDay())
                    .build();
        });
        given(projectMemberRepository.save(any(ProjectMember.class))).willAnswer(invocation -> {
            ProjectMember member = invocation.getArgument(0);
            return ProjectMember.builder()
                    .id(20L)
                    .user(member.getUser())
                    .project(member.getProject())
                    .role(member.getRole())
                    .status(member.getStatus())
                    .build();
        });

        ProjectCreateResponse response = projectCreateService.create(
                1L,
                new ProjectCreateRequest("  Plog API  ", projectType, endDay)
        );

        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.projectName()).isEqualTo("Plog API");
        assertThat(response.projectType()).isEqualTo(projectType);
        assertThat(response.status()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(response.startDay()).isEqualTo(today);
        assertThat(response.endDay()).isEqualTo(endDay);
        assertThat(response.myProjectMemberId()).isEqualTo(20L);
        assertThat(response.myRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(response.invite().inviteCode()).isEqualTo(RAW_INVITE_TOKEN);
        assertThat(response.invite().inviteUrl()).isEqualTo(INVITE_BASE_URL + "/" + RAW_INVITE_TOKEN);

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        ArgumentCaptor<ProjectMember> memberCaptor = ArgumentCaptor.forClass(ProjectMember.class);
        ArgumentCaptor<ChatRoom> chatRoomCaptor = ArgumentCaptor.forClass(ChatRoom.class);
        InOrder persistenceOrder = inOrder(
                projectRepository,
                projectMemberRepository,
                chatRoomRepository
        );
        persistenceOrder.verify(projectRepository).save(projectCaptor.capture());
        persistenceOrder.verify(projectMemberRepository).save(memberCaptor.capture());
        persistenceOrder.verify(chatRoomRepository).save(chatRoomCaptor.capture());

        Project savedProject = projectCaptor.getValue();
        assertThat(savedProject.getProjectName()).isEqualTo("Plog API");
        assertThat(savedProject.getInviteTokenHash()).isEqualTo(INVITE_TOKEN_HASH);
        assertThat(savedProject.getInviteTokenEncrypted()).isEqualTo(ENCRYPTED_INVITE_TOKEN);
        assertThat(savedProject.getProjectType()).isEqualTo(projectType);
        assertThat(savedProject.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(savedProject.getStartDay()).isEqualTo(today);
        assertThat(savedProject.getEndDay()).isEqualTo(endDay);

        ProjectMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getUser()).isSameAs(user);
        assertThat(savedMember.getProject().getId()).isEqualTo(10L);
        assertThat(savedMember.getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(savedMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(chatRoomCaptor.getValue().getProject().getId()).isEqualTo(10L);
    }

    @Test
    void createsInviteUrlWithoutDuplicateSlashWhenBaseUrlHasTrailingSlash() {
        ProjectCreateService serviceWithTrailingSlash = new ProjectCreateService(
                userRepository,
                projectRepository,
                projectMemberRepository,
                chatRoomRepository,
                inviteTokenService,
                INVITE_BASE_URL + "/"
        );
        User user = User.createLocal("owner@plog.test", "encoded-password", "Owner", "owner");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        executeInviteTokenPersistence();
        given(projectRepository.save(any(Project.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(projectMemberRepository.save(any(ProjectMember.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ProjectCreateResponse response = serviceWithTrailingSlash.create(1L, validRequest("Plog API"));

        assertThat(response.invite().inviteUrl()).isEqualTo(INVITE_BASE_URL + "/" + RAW_INVITE_TOKEN);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "P", "123456789012345678901"})
    void rejectsAnInvalidProjectName(String projectName) {
        ProjectCreateRequest request = validRequest(projectName);

        assertProjectError(
                () -> projectCreateService.create(1L, request),
                ProjectErrorCode.INVALID_PROJECT_NAME
        );

        verifyNoInteractions(
                userRepository,
                projectRepository,
                projectMemberRepository,
                chatRoomRepository,
                inviteTokenService
        );
    }

    @ParameterizedTest
    @MethodSource("invalidEndDays")
    void rejectsAnEndDayThatIsNotAfterUtcToday(LocalDate endDay) {
        ProjectCreateRequest request = new ProjectCreateRequest("Plog API", ProjectType.DEVELOP, endDay);

        assertProjectError(
                () -> projectCreateService.create(1L, request),
                ProjectErrorCode.INVALID_PROJECT_END_DAY
        );

        verifyNoInteractions(
                userRepository,
                projectRepository,
                projectMemberRepository,
                chatRoomRepository,
                inviteTokenService
        );
    }

    @Test
    void rejectsANullPrincipalAsAnInvalidToken() {
        assertAuthError(
                () -> projectCreateService.create(null, validRequest("Plog API")),
                AuthErrorCode.INVALID_TOKEN
        );

        verifyNoInteractions(
                userRepository,
                projectRepository,
                projectMemberRepository,
                chatRoomRepository,
                inviteTokenService
        );
    }

    @Test
    void rejectsAPrincipalWhoseUserNoLongerExists() {
        given(userRepository.findById(404L)).willReturn(Optional.empty());
        executeInviteTokenPersistence();

        assertAuthError(
                () -> projectCreateService.create(404L, validRequest("Plog API")),
                AuthErrorCode.INVALID_TOKEN
        );

        verify(projectRepository, never()).save(any(Project.class));
        verify(projectMemberRepository, never()).save(any(ProjectMember.class));
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    private ProjectCreateRequest validRequest(String projectName) {
        return new ProjectCreateRequest(
                projectName,
                ProjectType.DEVELOP,
                LocalDate.now(ZoneOffset.UTC).plusDays(30)
        );
    }

    private void executeInviteTokenPersistence() {
        given(inviteTokenService.issueAndPersist(any())).willAnswer(invocation -> {
            Function<InviteTokenService.IssuedToken, Object> operation = invocation.getArgument(0);
            InviteTokenService.IssuedToken token = new InviteTokenService.IssuedToken(
                    RAW_INVITE_TOKEN,
                    INVITE_TOKEN_HASH,
                    ENCRYPTED_INVITE_TOKEN
            );
            Object value = operation.apply(token);
            return new InviteTokenService.IssuedResult<>(token, value);
        });
    }

    private void assertProjectError(Runnable operation, ProjectErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private void assertAuthError(Runnable operation, AuthErrorCode errorCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private static Stream<LocalDate> invalidEndDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return Stream.of(null, today, today.minusDays(1));
    }
}
