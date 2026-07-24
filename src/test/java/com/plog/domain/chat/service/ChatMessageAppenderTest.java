package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.event.ChatMessageSavedEvent;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ChatMessageAppenderTest {

    private ChatRoomRepository chatRoomRepository;
    private ChatMessageRepository chatMessageRepository;
    private ProjectMemberRepository projectMemberRepository;
    private ApplicationEventPublisher eventPublisher;
    private EntityManager entityManager;
    private ChatMessageAppender sut;

    @BeforeEach
    void setUp() {
        chatRoomRepository = mock(ChatRoomRepository.class);
        chatMessageRepository = mock(ChatMessageRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        entityManager = mock(EntityManager.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mock(Query.class));

        sut = new ChatMessageAppender(
                chatRoomRepository, chatMessageRepository, projectMemberRepository,
                entityManager, eventPublisher
        );
    }

    @Test
    void 신규_메시지면_저장하고_커밋후_이벤트를_발행한다() {
        Project project = Project
                .builder()
                .id(100L)
                .inviteTokenHash("dummy-invite-token-hash")
                .inviteTokenEncrypted("dummy-invite-token-encrypted")
                .build();
        ChatRoom room = ChatRoom.builder().id(1L).project(project).lastMessageSequence(0L).build();
        ProjectMember member = ProjectMember.builder()
                .id(5L).project(project).status(MemberStatus.ACTIVE).anNickname("모모").build();

        when(chatRoomRepository.findByIdForMessageAppend(1L)).thenReturn(Optional.of(room));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(100L, 10L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(1L, 5L, "client-1"))
                .thenReturn(Optional.empty());
        when(chatMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessage result = sut.appendByUser(1L, 10L, "client-1", "안녕하세요");

        assertThat(result.getMessage()).isEqualTo("안녕하세요");
        assertThat(result.getMessageSequence()).isEqualTo(1L);
        verify(chatMessageRepository).save(any());
        verify(eventPublisher).publishEvent(any(ChatMessageSavedEvent.class));
    }

    @Test
    void 같은_clientMessageId면_중복_저장하지_않고_기존_메시지를_반환한다() {
        Project project = Project
                .builder()
                .id(100L)
                .inviteTokenHash("dummy-invite-token-hash")
                .inviteTokenEncrypted("dummy-invite-token-encrypted")
                .build();
        ChatRoom room = ChatRoom.builder().id(1L).project(project).lastMessageSequence(3L).build();
        ProjectMember member = ProjectMember.builder()
                .id(5L).project(project).status(MemberStatus.ACTIVE).anNickname("모모").build();
        ChatMessage existing = ChatMessage.create(room, member, "이미 보낸 메시지", 3L, "client-1");

        when(chatRoomRepository.findByIdForMessageAppend(1L)).thenReturn(Optional.of(room));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(100L, 10L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(1L, 5L, "client-1"))
                .thenReturn(Optional.of(existing));

        ChatMessage result = sut.appendByUser(1L, 10L, "client-1", "이미 보낸 메시지");

        assertThat(result).isSameAs(existing);
        verify(chatMessageRepository, never()).save(any());
        verify(eventPublisher).publishEvent(any(ChatMessageSavedEvent.class)); // 재전송이어도 재브로드캐스트 유도를 위해 발행은 됨
    }

    @Test
    void 프로젝트의_활성_멤버가_아니면_거부한다() {
        Project project = Project
                .builder()
                .id(100L)
                .inviteTokenHash("dummy-invite-token-hash")
                .inviteTokenEncrypted("dummy-invite-token-encrypted")
                .build();
        ChatRoom room = ChatRoom.builder().id(1L).project(project).lastMessageSequence(0L).build();

        when(chatRoomRepository.findByIdForMessageAppend(1L)).thenReturn(Optional.of(room));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(100L, 999L, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.appendByUser(1L, 999L, "client-1", "안녕하세요"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_채팅방이면_거부한다() {
        when(chatRoomRepository.findByIdForMessageAppend(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.appendByUser(999L, 10L, "client-1", "안녕하세요"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);
    }
}