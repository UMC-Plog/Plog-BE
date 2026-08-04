package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.chat.dto.response.ChatReadResponse;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.entity.ChatRoomReadCursor;
import com.plog.domain.chat.event.ChatReadUpdatedEvent;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomReadCursorRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.chat.repository.projection.ChatRoomUnreadCount;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ChatReadServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long USER_ID = 100L;
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 200L;
    private static final Long LAST_READ_MESSAGE_ID = 5L;
    private static final Long LAST_READ_SEQUENCE = 5L;
    private static final long UNREAD_COUNT = 3L;

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ChatRoomReadCursorRepository chatRoomReadCursorRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChatReadService chatReadService;

    @BeforeEach
    void setUp() {
        chatReadService = new ChatReadService(
                chatRoomRepository, chatMessageRepository, projectMemberRepository,
                chatRoomReadCursorRepository, eventPublisher);

        Project project = mock(Project.class);
        when(project.getId()).thenReturn(PROJECT_ID);

        ChatRoom room = mock(ChatRoom.class);
        when(room.getProject()).thenReturn(project);

        ChatMessage message = mock(ChatMessage.class);

        ProjectMember member = ProjectMember.builder()
                .id(MEMBER_ID)
                .status(MemberStatus.ACTIVE)
                .build();

        ChatRoomReadCursor cursor = mock(ChatRoomReadCursor.class);
        when(cursor.getLastReadMessageSequence()).thenReturn(LAST_READ_SEQUENCE);

        ChatRoomUnreadCount unreadCount = mock(ChatRoomUnreadCount.class);
        when(unreadCount.getUnreadCount()).thenReturn(UNREAD_COUNT);

        when(chatRoomRepository.findAccessibleRoom(ROOM_ID, USER_ID, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(room));
        when(chatMessageRepository.findByIdAndChatRoomIdAndMessageSequenceIsNotNull(LAST_READ_MESSAGE_ID, ROOM_ID))
                .thenReturn(Optional.of(message));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(PROJECT_ID, USER_ID, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(chatRoomReadCursorRepository.findAccessibleCursor(ROOM_ID, USER_ID, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(cursor));
        when(chatRoomReadCursorRepository.findUnreadCount(ROOM_ID, USER_ID, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(unreadCount));
    }

    @Test
    void 읽음_처리_후_본인_userId로_이벤트를_발행한다() {
        chatReadService.markAsRead(ROOM_ID, USER_ID, LAST_READ_MESSAGE_ID);

        ArgumentCaptor<ChatReadUpdatedEvent> captor = ArgumentCaptor.forClass(ChatReadUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        ChatReadUpdatedEvent event = captor.getValue();
        assertThat(event.roomId()).isEqualTo(ROOM_ID);
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.lastReadMessageSequence()).isEqualTo(LAST_READ_SEQUENCE);
        assertThat(event.unreadMessageCount()).isEqualTo(UNREAD_COUNT);
    }

    @Test
    void 발행된_이벤트와_REST_응답의_값이_동일하다() {
        ChatReadResponse response = chatReadService.markAsRead(ROOM_ID, USER_ID, LAST_READ_MESSAGE_ID);

        ArgumentCaptor<ChatReadUpdatedEvent> captor = ArgumentCaptor.forClass(ChatReadUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ChatReadUpdatedEvent event = captor.getValue();

        assertThat(event.roomId()).isEqualTo(response.roomId());
        assertThat(event.lastReadMessageSequence()).isEqualTo(response.lastReadMessageSequence());
        assertThat(event.unreadMessageCount()).isEqualTo(response.unreadMessageCount());
    }
}