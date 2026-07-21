package com.plog.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.notification.entity.FcmToken;
import com.plog.domain.notification.event.ChatMentionEvent;
import com.plog.domain.notification.repository.FcmTokenRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.user.entity.User;
import com.plog.infrastructure.fcm.FcmDeliveryException;
import com.plog.infrastructure.fcm.FcmGateway;
import com.plog.infrastructure.fcm.FcmMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MentionNotificationServiceTest {
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private FcmTokenRepository fcmTokenRepository;
    @Mock
    private FcmGateway fcmGateway;

    private MentionNotificationService service;
    private ProjectMember sender;
    private ProjectMember target;
    private ProjectMember exitedTarget;

    @BeforeEach
    void setUp() {
        service = new MentionNotificationService(projectMemberRepository, fcmTokenRepository, fcmGateway);
        Project project = project(10L, "Plog");
        sender = member(1L, 101L, project, MemberStatus.ACTIVE, "곰곰");
        target = member(2L, 102L, project, MemberStatus.ACTIVE, "포도");
        exitedTarget = member(3L, 103L, project, MemberStatus.EXIT, "사과");
    }

    @Test
    void sendsOnceForDuplicatedMentionAndExcludesSenderAndExitedMember() {
        FcmToken token = token("token-102");
        when(projectMemberRepository.findAllByIdIn(anyCollection()))
                .thenReturn(List.of(sender, target, exitedTarget));
        when(fcmTokenRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of(token));

        service.send(new ChatMentionEvent(10L, 20L, 1L, List.of(2L, 2L, 1L, 3L), " 확인 부탁해요 "));

        ArgumentCaptor<FcmMessage> captor = ArgumentCaptor.forClass(FcmMessage.class);
        verify(fcmGateway).send(captor.capture());
        FcmMessage message = captor.getValue();
        assertThat(message.token()).isEqualTo("token-102");
        assertThat(message.title()).isEqualTo("Plog 멘션");
        assertThat(message.body()).isEqualTo("곰곰님: 확인 부탁해요");
        assertThat(message.data()).containsEntry("projectId", "10")
                .containsEntry("chatId", "20")
                .containsEntry("type", "CHAT_MENTION");
    }

    @Test
    void physicallyDeletesInvalidTokenWithoutRetrying() {
        FcmToken token = token("invalid-token");
        when(projectMemberRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(sender, target));
        when(fcmTokenRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of(token));
        org.mockito.Mockito.doThrow(new FcmDeliveryException(true, new RuntimeException("invalid")))
                .when(fcmGateway).send(any(FcmMessage.class));

        service.send(new ChatMentionEvent(10L, 20L, 1L, List.of(2L), "hello"));

        verify(fcmTokenRepository).deleteByToken("invalid-token");
        verify(fcmGateway).send(any(FcmMessage.class));
    }

    private Project project(Long id, String name) {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(id);
        when(project.getProjectName()).thenReturn(name);
        return project;
    }

    private ProjectMember member(
            Long id,
            Long userId,
            Project project,
            MemberStatus status,
            String nickname
    ) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        ProjectMember member = mock(ProjectMember.class);
        lenient().when(member.getId()).thenReturn(id);
        lenient().when(member.getUser()).thenReturn(user);
        lenient().when(member.getProject()).thenReturn(project);
        lenient().when(member.getStatus()).thenReturn(status);
        lenient().when(member.getAnNickname()).thenReturn(nickname);
        return member;
    }

    private FcmToken token(String value) {
        FcmToken token = mock(FcmToken.class);
        when(token.getToken()).thenReturn(value);
        return token;
    }
}
