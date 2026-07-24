package com.plog.domain.chat.event;

import com.plog.domain.chat.dto.response.ChatMessageResponse;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageBroadcastListener {

    private static final String CHAT_ROOM_TOPIC_PREFIX = "/topic/chat-rooms/";
    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MILLIS = 200L;

    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSaved(ChatMessageSavedEvent event) {
        chatMessageRepository.findById(event.chatMessageId())
                .ifPresentOrElse(this::broadcastWithRetry,
                        () -> log.warn("브로드캐스트 대상 메시지를 찾을 수 없습니다. chatId={}", event.chatMessageId()));
    }

    private void broadcastWithRetry(ChatMessage chatMessage) {
        String destination = CHAT_ROOM_TOPIC_PREFIX + chatMessage.getChatRoom().getId();
        ChatMessageResponse response = toResponse(chatMessage);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                messagingTemplate.convertAndSend(destination, response);
                return;
            } catch (MessagingException exception) {
                log.warn("채팅 메시지 브로드캐스트 실패 ({}회차) chatId={}", attempt, chatMessage.getId(), exception);
                sleep(BACKOFF_MILLIS * attempt);
            }
        }
        log.error("채팅 메시지 브로드캐스트 최종 실패. DB엔 저장됨 - 클라이언트 재전송(동일 clientMessageId) 시 재시도됨. chatId={}",
                chatMessage.getId());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private ChatMessageResponse toResponse(ChatMessage chatMessage) {
        var sender = chatMessage.getProjectMember();
        return new ChatMessageResponse(
                chatMessage.getId(),
                chatMessage.getChatRoom().getId(),
                chatMessage.getMessageSequence(),
                sender.getId(),
                sender.getAnNickname(),
                sender.getUser().getProfileImageUrl(),
                chatMessage.getMessage(),
                TimeUtil.toInstant(chatMessage.getCreatedAt())
        );
    }
}