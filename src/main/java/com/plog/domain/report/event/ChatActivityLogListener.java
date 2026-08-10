package com.plog.domain.report.event;

import com.plog.domain.chat.event.ChatMessageSavedEvent;
import com.plog.domain.report.service.ChatActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatActivityLogListener {

    private final ChatActivityLogService activityLogService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSaved(ChatMessageSavedEvent event) {
        try {
            activityLogService.collectMessageSaved(event.chatMessageId());
        } catch (RuntimeException exception) {
            // AFTER_COMMIT 비동기 후처리이므로 채팅 저장은 이미 확정됐다. 유실은 로그로 추적한다.
            log.error("chat_report_activity_collection_failed chatMessageId={}",
                    event.chatMessageId(), exception);
        }
    }
}
