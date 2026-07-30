package com.plog.domain.chat.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.chat.dto.response.ChatMessageResponse;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.chat.service.ChatAttachmentResponseMapper;
import com.plog.domain.chat.service.ChatMessageQueryService;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatMessageBroadcastListenerTest {

    private static final Long CHAT_ID = 3L;
    private static final Long ROOM_ID = 10L;
    private static final Long SENDER_MEMBER_ID = 10L;
    private static final Long VIEWER_USER_ID = 100L;

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatAttachmentRepository chatAttachmentRepository;
    @Mock private ChatAttachmentResponseMapper chatAttachmentResponseMapper;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ChatMessageBroadcastListener listener;
    private ChatRoom room;
    private ChatMessage message;

    @BeforeEach
    void setUp() {
        listener = new ChatMessageBroadcastListener(
                chatMessageRepository, chatAttachmentRepository, chatAttachmentResponseMapper, messagingTemplate);

        User senderUser = mock(User.class);
        lenient().when(senderUser.getNickname()).thenReturn("지현");
        lenient().when(senderUser.getProfilePreset()).thenReturn(ProfilePreset.TIGER);

        // anNickname 미설정(프로젝트 별칭 없음)이 실제 데이터의 기본 상태다.
        ProjectMember sender = ProjectMember.builder()
                .id(SENDER_MEMBER_ID)
                .user(senderUser)
                .status(MemberStatus.ACTIVE)
                .build();

        room = mock(ChatRoom.class);
        lenient().when(room.getId()).thenReturn(ROOM_ID);

        message = mock(ChatMessage.class);
        lenient().when(message.getId()).thenReturn(CHAT_ID);
        lenient().when(message.getChatRoom()).thenReturn(room);
        lenient().when(message.getMessageSequence()).thenReturn(3L);
        lenient().when(message.getProjectMember()).thenReturn(sender);
        lenient().when(message.getMessage()).thenReturn("ㅎㅇ");

        lenient().when(chatMessageRepository.findWithRoomAndSenderById(CHAT_ID)).thenReturn(Optional.of(message));
        lenient().when(chatAttachmentRepository.findAllByChatMessageIdOrderByIdAsc(CHAT_ID)).thenReturn(List.of());
        lenient().when(chatAttachmentResponseMapper.toResponses(List.of())).thenReturn(List.of());
    }

    @Test
    void 브로드캐스트_응답의_senderNickname은_null이_아니고_멤버_표시_닉네임과_일치한다() {
        listener.onChatMessageSaved(new ChatMessageSavedEvent(CHAT_ID));

        ChatMessageResponse response = capturedBroadcast();

        assertThat(response.senderNickname()).isNotNull();
        assertThat(response.senderNickname()).isEqualTo("지현");
    }

    @Test
    void 브로드캐스트_응답의_나머지_필드도_그대로_매핑된다() {
        listener.onChatMessageSaved(new ChatMessageSavedEvent(CHAT_ID));

        ChatMessageResponse response = capturedBroadcast();

        assertThat(response.chatId()).isEqualTo(CHAT_ID);
        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        assertThat(response.messageSequence()).isEqualTo(3L);
        assertThat(response.senderMemberId()).isEqualTo(SENDER_MEMBER_ID);
        assertThat(response.profilePreset()).isEqualTo(ProfilePreset.TIGER);
        assertThat(response.message()).isEqualTo("ㅎㅇ");
        assertThat(response.attachments()).isEmpty();
    }

    @Test
    void REST_상세_응답과_실시간_응답의_발신자_정보가_일치한다() {
        when(chatRoomRepository.findAccessibleRoom(ROOM_ID, VIEWER_USER_ID, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(room));
        ChatMessageQueryService queryService = new ChatMessageQueryService(
                chatRoomRepository, chatMessageRepository, chatAttachmentRepository, chatAttachmentResponseMapper);

        ChatMessageResponse restResponse = queryService.getMessageDetail(CHAT_ID, VIEWER_USER_ID);

        listener.onChatMessageSaved(new ChatMessageSavedEvent(CHAT_ID));
        ChatMessageResponse broadcastResponse = capturedBroadcast();

        assertThat(broadcastResponse.senderMemberId()).isEqualTo(restResponse.senderMemberId());
        assertThat(broadcastResponse.senderNickname()).isEqualTo(restResponse.senderNickname());
        assertThat(broadcastResponse.profilePreset()).isEqualTo(restResponse.profilePreset());
    }

    private ChatMessageResponse capturedBroadcast() {
        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/chat-rooms/" + ROOM_ID), captor.capture());
        return captor.getValue();
    }
}