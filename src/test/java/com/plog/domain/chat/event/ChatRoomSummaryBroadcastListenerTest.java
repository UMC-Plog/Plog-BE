package com.plog.domain.chat.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.plog.domain.chat.dto.response.ChatRoomSummaryMessage;
import com.plog.domain.chat.repository.ChatRoomReadCursorRepository;
import com.plog.domain.chat.repository.projection.ChatRoomParticipantUnreadCount;
import com.plog.domain.project.entity.MemberStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatRoomSummaryBroadcastListenerTest {

    private static final Long ROOM_ID = 10L;
    private static final String CHAT_UPDATE_QUEUE = "/queue/chat-update";

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ChatRoomReadCursorRepository chatRoomReadCursorRepository;

    @Test
    void 참여자마다_배치_조회된_unreadCount로_각자에게_push한다() {
        ChatRoomSummaryBroadcastListener listener =
                new ChatRoomSummaryBroadcastListener(messagingTemplate, chatRoomReadCursorRepository);

        ChatRoomParticipantUnreadCount countForUserA = mock(ChatRoomParticipantUnreadCount.class);
        when(countForUserA.getUserId()).thenReturn(1L);
        when(countForUserA.getUnreadCount()).thenReturn(3L);
        ChatRoomParticipantUnreadCount countForUserB = mock(ChatRoomParticipantUnreadCount.class);
        when(countForUserB.getUserId()).thenReturn(2L);
        when(countForUserB.getUnreadCount()).thenReturn(1L);

        when(chatRoomReadCursorRepository.findUnreadCountsForRoom(ROOM_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of(countForUserA, countForUserB));

        listener.onChatRoomSummaryUpdated(
                new ChatRoomSummaryUpdatedEvent(ROOM_ID, 5L, "회의록.pdf", List.of(1L, 2L)));

        // 참여자 수(2명)와 무관하게 unreadCount 조회는 쿼리 1번(배치)으로 끝난다
        verify(chatRoomReadCursorRepository, times(1))
                .findUnreadCountsForRoom(ROOM_ID, MemberStatus.ACTIVE);

        ArgumentCaptor<ChatRoomSummaryMessage> captor = ArgumentCaptor.forClass(ChatRoomSummaryMessage.class);
        verify(messagingTemplate).convertAndSendToUser(eq("1"), eq(CHAT_UPDATE_QUEUE), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("ROOM_SUMMARY");
        assertThat(captor.getValue().roomId()).isEqualTo(ROOM_ID);
        assertThat(captor.getValue().messageSequence()).isEqualTo(5L);
        assertThat(captor.getValue().latestMessage()).isEqualTo("회의록.pdf");
        assertThat(captor.getValue().unreadMessageCount()).isEqualTo(3L);

        verify(messagingTemplate).convertAndSendToUser(eq("2"), eq(CHAT_UPDATE_QUEUE), any(ChatRoomSummaryMessage.class));
    }

    @Test
    void 배치_조회_결과에_없는_유저는_unreadCount_0으로_push한다() {
        ChatRoomSummaryBroadcastListener listener =
                new ChatRoomSummaryBroadcastListener(messagingTemplate, chatRoomReadCursorRepository);

        when(chatRoomReadCursorRepository.findUnreadCountsForRoom(ROOM_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of());

        listener.onChatRoomSummaryUpdated(new ChatRoomSummaryUpdatedEvent(ROOM_ID, 5L, "hi", List.of(1L)));

        ArgumentCaptor<ChatRoomSummaryMessage> captor = ArgumentCaptor.forClass(ChatRoomSummaryMessage.class);
        verify(messagingTemplate).convertAndSendToUser(eq("1"), eq(CHAT_UPDATE_QUEUE), captor.capture());
        assertThat(captor.getValue().unreadMessageCount()).isZero();
    }

    @Test
    void 대상이_없으면_배치_조회조차_하지_않는다() {
        ChatRoomSummaryBroadcastListener listener =
                new ChatRoomSummaryBroadcastListener(messagingTemplate, chatRoomReadCursorRepository);

        listener.onChatRoomSummaryUpdated(new ChatRoomSummaryUpdatedEvent(ROOM_ID, 5L, "hi", List.of()));

        verifyNoInteractions(chatRoomReadCursorRepository, messagingTemplate);
    }

    @Test
    void push가_일시적으로_실패해도_재시도해서_결국_전송한다() {
        ChatRoomSummaryBroadcastListener listener =
                new ChatRoomSummaryBroadcastListener(messagingTemplate, chatRoomReadCursorRepository);
        when(chatRoomReadCursorRepository.findUnreadCountsForRoom(ROOM_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of());

        doThrow(new MessageDeliveryException("일시적 실패"))
                .doThrow(new MessageDeliveryException("일시적 실패"))
                .doNothing()
                .when(messagingTemplate).convertAndSendToUser(any(), any(), any());

        listener.onChatRoomSummaryUpdated(new ChatRoomSummaryUpdatedEvent(ROOM_ID, 5L, "hi", List.of(1L)));

        verify(messagingTemplate, times(3)).convertAndSendToUser(any(), any(), any());
    }
}