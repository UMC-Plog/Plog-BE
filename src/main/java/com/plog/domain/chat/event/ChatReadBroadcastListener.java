package com.plog.domain.chat.event;

import com.plog.domain.chat.dto.response.ChatReadUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// /user/queue/chat-update 로 읽음 처리 결과를 push한다(type: READ_UPDATE).
// 대상은 읽음 처리를 수행한 본인 — 같은 계정의 다른 세션(기기/탭)이 REST 폴링 없이 unread count를 동기화하도록 하는 용도
// 같은 채널로 새 메시지 도착 시 방 참여자 전원에게 가는 목록 갱신 push(type: ROOM_SUMMARY)는
// ChatRoomSummaryBroadcastListener가 담당한다 — 트리거/대상이 서로 달라 이벤트/리스너를 분리했다.
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatReadBroadcastListener {

    private static final String CHAT_UPDATE_QUEUE = "/queue/chat-update";
    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MILLIS = 200L;

    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatReadUpdated(ChatReadUpdatedEvent event) {
        ChatReadUpdateMessage payload = ChatReadUpdateMessage.of(
                event.roomId(), event.lastReadMessageSequence(), event.unreadMessageCount());
        sendWithRetry(String.valueOf(event.userId()), payload);
    }

    private void sendWithRetry(String userId, ChatReadUpdateMessage payload) {
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                messagingTemplate.convertAndSendToUser(userId, CHAT_UPDATE_QUEUE, payload);
                return;
            } catch (MessagingException exception) {
                log.warn("읽음 상태 push 실패 ({}회차) userId={} roomId={}", attempt, userId, payload.roomId(), exception);
                if (attempt <= MAX_RETRIES && !sleep(BACKOFF_MILLIS * attempt)) {
                    log.warn("읽음 상태 push 재시도 중 인터럽트되어 중단합니다. userId={} roomId={}", userId, payload.roomId());
                    return;
                }
            }
        }
        log.error("읽음 상태 push 최종 실패. DB엔 반영됨 - 실시간 동기화만 누락. userId={} roomId={}",
                userId, payload.roomId());
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