package com.plog.domain.notification.event;

import com.plog.domain.notification.service.MentionNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMentionNotificationListener {
    private final MentionNotificationService mentionNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMention(ChatMentionEvent event) {
        mentionNotificationService.send(event);
    }
}
