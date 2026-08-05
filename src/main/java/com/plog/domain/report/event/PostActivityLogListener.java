package com.plog.domain.report.event;

import com.plog.domain.post.event.CommentCreatedEvent;
import com.plog.domain.post.event.PostCreatedEvent;
import com.plog.domain.report.service.PostActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PostActivityLogListener {
    private final PostActivityLogService activityLogService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCreated(PostCreatedEvent event) {
        activityLogService.collectPostCreated(
                event.postId(), event.projectMemberId(), event.content(), event.occurredAt());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        activityLogService.collectCommentCreated(event.commentId(), event.postId(),
                event.projectMemberId(), event.content(), event.occurredAt());
    }
}
