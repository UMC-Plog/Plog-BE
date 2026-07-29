package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatAttachmentRepository chatAttachmentRepository;
    @Mock private ChatAttachmentResponseMapper chatAttachmentResponseMapper;

    private ChatMessageQueryService service;

    @BeforeEach
    void setUp() {
        service = new ChatMessageQueryService(
                chatRoomRepository, chatMessageRepository, chatAttachmentRepository, chatAttachmentResponseMapper);
    }

    @Test
    void 존재하지_않는_메시지는_CHAT_MESSAGE_NOT_FOUND() {
        when(chatMessageRepository.findWithRoomAndSenderById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessageDetail(1L, 100L))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }

    @Test
    void 프로젝트_비접근자는_FORBIDDEN_CHAT_ROOM_ACCESS() {
        ChatRoom room = mock(ChatRoom.class);
        when(room.getId()).thenReturn(5L);
        ChatMessage message = mock(ChatMessage.class);
        lenient().when(message.getChatRoom()).thenReturn(room);

        when(chatMessageRepository.findWithRoomAndSenderById(1L)).thenReturn(Optional.of(message));
        when(chatRoomRepository.findAccessibleRoom(5L, 100L, MemberStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessageDetail(1L, 100L))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));
    }

    @Test
    void room이_없는_레거시_메시지는_CHAT_MESSAGE_NOT_FOUND로_처리() {
        ChatMessage message = mock(ChatMessage.class);
        lenient().when(message.getChatRoom()).thenReturn(null);
        when(chatMessageRepository.findWithRoomAndSenderById(1L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.getMessageDetail(1L, 100L))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getErrorCode())
                        .isEqualTo(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }

    @Test
    void 정상_조회시_필드가_모두_매핑된다() {
        Project project = mock(Project.class);
        ChatRoom room = mock(ChatRoom.class);
        lenient().when(room.getId()).thenReturn(5L);
        lenient().when(room.getProject()).thenReturn(project);

        User senderUser = mock(User.class);
        lenient().when(senderUser.getProfilePreset()).thenReturn(ProfilePreset.OTTER);
        ProjectMember sender = mock(ProjectMember.class);
        lenient().when(sender.getId()).thenReturn(11L);
        lenient().when(sender.getAnNickname()).thenReturn("곰곰");
        lenient().when(sender.getUser()).thenReturn(senderUser);

        ChatMessage message = mock(ChatMessage.class);
        lenient().when(message.getId()).thenReturn(1L);
        lenient().when(message.getChatRoom()).thenReturn(room);
        lenient().when(message.getMessageSequence()).thenReturn(7L);
        lenient().when(message.getProjectMember()).thenReturn(sender);
        lenient().when(message.getMessage()).thenReturn("안녕하세요");

        when(chatMessageRepository.findWithRoomAndSenderById(1L)).thenReturn(Optional.of(message));
        when(chatRoomRepository.findAccessibleRoom(5L, 100L, MemberStatus.ACTIVE)).thenReturn(Optional.of(room));
        when(chatAttachmentRepository.findAllByChatMessageIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(chatAttachmentResponseMapper.toResponses(List.of())).thenReturn(List.of());

        var response = service.getMessageDetail(1L, 100L);

        assertThat(response.chatId()).isEqualTo(1L);
        assertThat(response.roomId()).isEqualTo(5L);
        assertThat(response.messageSequence()).isEqualTo(7L);
        assertThat(response.senderMemberId()).isEqualTo(11L);
        assertThat(response.senderNickname()).isEqualTo("곰곰");
        assertThat(response.message()).isEqualTo("안녕하세요");
    }
}