package com.plog.domain.chat.event;

import com.plog.domain.chat.dto.response.ChatRoomSummaryMessage;
import com.plog.domain.chat.repository.ChatRoomReadCursorRepository;
import com.plog.domain.chat.repository.projection.ChatRoomParticipantUnreadCount;
import com.plog.domain.project.entity.MemberStatus;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// /user/queue/chat-update 로 채팅방 목록 갱신(unreadCount, 최신 메시지 미리보기)을 push한다(type: ROOM_SUMMARY).
// 대상은 새 메시지가 저장된 방의 발신자를 제외한 참여자 전원 — 멘션 여부와 무관하게 전원에게 간다
// (멘션된 사람도 목록 미리보기/unreadCount는 갱신되어야 하므로).
// unreadCount는 참여자마다 다르지만, 참여자 수만큼 쿼리를 반복하면 메시지 1건당 N번 쿼리가 나가므로
// findUnreadCountsForRoom으로 방 전체 참여자의 unreadCount를 쿼리 1번에 배치 조회한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomSummaryBroadcastListener {

    private static final String CHAT_UPDATE_QUEUE = "/queue/chat-update";
    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MILLIS = 200L;

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomReadCursorRepository chatRoomReadCursorRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatRoomSummaryUpdated(ChatRoomSummaryUpdatedEvent event) {
        if (event.targetUserIds().isEmpty()) {
            return;
        }

        Map<Long, Long> unreadCountByUserId = chatRoomReadCursorRepository
                .findUnreadCountsForRoom(event.roomId(), MemberStatus.ACTIVE).stream()
                .collect(Collectors.toMap(
                        ChatRoomParticipantUnreadCount::getUserId,
                        ChatRoomParticipantUnreadCount::getUnreadCount));

        for (Long userId : event.targetUserIds()) {
            long unreadCount = unreadCountByUserId.getOrDefault(userId, 0L);
            ChatRoomSummaryMessage payload = ChatRoomSummaryMessage.of(
                    event.roomId(), event.messageSequence(), event.latestMessage(), unreadCount);
            sendWithRetry(String.valueOf(userId), payload);
        }
    }

    private void sendWithRetry(String userId, ChatRoomSummaryMessage payload) {
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                messagingTemplate.convertAndSendToUser(userId, CHAT_UPDATE_QUEUE, payload);
                return;
            } catch (MessagingException exception) {
                log.warn("채팅방 목록 갱신 push 실패 ({}회차) userId={} roomId={}",
                        attempt, userId, payload.roomId(), exception);
                if (attempt <= MAX_RETRIES && !sleep(BACKOFF_MILLIS * attempt)) {
                    log.warn("채팅방 목록 갱신 push 재시도 중 인터럽트되어 중단합니다. userId={} roomId={}",
                            userId, payload.roomId());
                    return;
                }
            }
        }
        log.error("채팅방 목록 갱신 push 최종 실패. userId={} roomId={}", userId, payload.roomId());
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}