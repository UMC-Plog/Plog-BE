package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.chat.dto.response.ChatChannelParticipantResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.user.entity.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatChannelParticipantServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    private ChatChannelParticipantService service;

    @BeforeEach
    void setUp() {
        service = new ChatChannelParticipantService(projectMemberRepository);
    }

    @Test
    void groupsAllActiveParticipantsByProject() {
        Project firstProject = project(10L);
        Project secondProject = project(20L);
        List<ProjectMember> activeMembers = List.of(
                member(firstProject, user(1L, "바나", "https://image.test/1.png")),
                member(firstProject, user(2L, "팀원", null)),
                member(secondProject, user(3L, "개발자", "https://image.test/3.png"))
        );
        given(projectMemberRepository.findActiveMembers(
                List.of(10L, 20L), MemberStatus.ACTIVE
        )).willReturn(activeMembers);

        Map<Long, List<ChatChannelParticipantResponse>> participants =
                service.getParticipantsByProjectIds(List.of(10L, 20L));

        assertThat(participants.get(10L)).containsExactly(
                new ChatChannelParticipantResponse(1L, "바나", "https://image.test/1.png"),
                new ChatChannelParticipantResponse(2L, "팀원", null)
        );
        assertThat(participants.get(20L)).containsExactly(
                new ChatChannelParticipantResponse(3L, "개발자", "https://image.test/3.png")
        );
    }

    @Test
    void skipsTheMemberQueryForAnEmptyProjectList() {
        assertThat(service.getParticipantsByProjectIds(List.of())).isEmpty();

        verifyNoInteractions(projectMemberRepository);
    }

    private Project project(Long projectId) {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(projectId);
        return project;
    }

    private ProjectMember member(Project project, User user) {
        ProjectMember member = mock(ProjectMember.class);
        given(member.getProject()).willReturn(project);
        given(member.getUser()).willReturn(user);
        return member;
    }

    private User user(Long userId, String nickname, String profileImageUrl) {
        User user = mock(User.class);
        given(user.getId()).willReturn(userId);
        given(user.getNickname()).willReturn(nickname);
        given(user.getProfileImageUrl()).willReturn(profileImageUrl);
        return user;
    }
}
