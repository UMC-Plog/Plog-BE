package com.plog.domain.chat.event;

import com.plog.domain.chat.dto.response.ChatReadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// /user/queue/chat-update 로 읽음 처리 결과를 push한다.
// 대상은 읽음 처리를 수행한 본인 — 같은 계정의 다른 세션(기기/탭)이 REST 폴링 없이
// unread count를 동기화하도록 하는 용도다. 방의 다른 참여자에게 "누가 어디까지 읽었는지"를
// 알리는 건 별개 기능(필요 시 /topic/chat-rooms/{roomId} 브로드캐스트로 추가)이다.
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
        ChatReadResponse payload = new ChatReadResponse(
                event.roomId(), event.lastReadMessageSequence(), event.unreadMessageCount());
        sendWithRetry(String.valueOf(event.userId()), payload);
    }

    private void sendWithRetry(String userId, ChatReadResponse payload) {
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