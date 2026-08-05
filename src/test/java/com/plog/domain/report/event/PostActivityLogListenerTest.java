package com.plog.domain.report.event;

import static org.mockito.Mockito.verify;

import com.plog.domain.post.event.CommentCreatedEvent;
import com.plog.domain.post.event.PostCreatedEvent;
import com.plog.domain.report.service.PostActivityLogService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class PostActivityLogListenerTest {
    @Mock private PostActivityLogService activityLogService;

    @Test
    void 게시글과_댓글_생성_이벤트를_수집_서비스에_전달한다() {
        PostActivityLogListener listener = new PostActivityLogListener(activityLogService);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 4, 12, 0);

        listener.onPostCreated(new PostCreatedEvent(11L, 7L, "게시글", occurredAt));
        listener.onCommentCreated(new CommentCreatedEvent(31L, 11L, 7L, "댓글", occurredAt));

        verify(activityLogService).collectPostCreated(11L, 7L, "게시글", occurredAt);
        verify(activityLogService).collectCommentCreated(31L, 11L, 7L, "댓글", occurredAt);
    }

    @Test
    void 원본_트랜잭션_커밋_후에만_수집한다() throws NoSuchMethodException {
        assertAfterCommit("onPostCreated", PostCreatedEvent.class);
        assertAfterCommit("onCommentCreated", CommentCreatedEvent.class);
    }

    private void assertAfterCommit(String methodName, Class<?> eventType) throws NoSuchMethodException {
        TransactionalEventListener annotation = PostActivityLogListener.class
                .getMethod(methodName, eventType)
                .getAnnotation(TransactionalEventListener.class);
        org.assertj.core.api.Assertions.assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
