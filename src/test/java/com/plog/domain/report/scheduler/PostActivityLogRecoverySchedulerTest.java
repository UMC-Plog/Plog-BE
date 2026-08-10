package com.plog.domain.report.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.report.repository.projection.CommentLogRecoveryTarget;
import com.plog.domain.report.repository.projection.PostLogRecoveryTarget;
import com.plog.domain.report.service.PostActivityLogService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class PostActivityLogRecoverySchedulerTest {

    @Mock
    private ReportActivityLogRepository repository;
    @Mock
    private PostActivityLogService service;
    @InjectMocks
    private PostActivityLogRecoveryScheduler scheduler;

    @Test
    void recollectsMissingPostAndCommentLogs() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 11, 9, 0);
        PostLogRecoveryTarget post = mock(PostLogRecoveryTarget.class);
        when(post.getPostId()).thenReturn(1L);
        when(post.getMemberId()).thenReturn(2L);
        when(post.getContent()).thenReturn("post");
        when(post.getOccurredAt()).thenReturn(occurredAt);
        CommentLogRecoveryTarget comment = mock(CommentLogRecoveryTarget.class);
        when(comment.getCommentId()).thenReturn(3L);
        when(comment.getPostId()).thenReturn(1L);
        when(comment.getMemberId()).thenReturn(2L);
        when(comment.getContent()).thenReturn("comment");
        when(comment.getOccurredAt()).thenReturn(occurredAt);
        when(repository.findPostsMissingActivityLog(any(), eq(Limit.of(200)))).thenReturn(List.of(post));
        when(repository.findCommentsMissingActivityLog(any(), eq(Limit.of(200)))).thenReturn(List.of(comment));

        scheduler.recollectMissing();

        verify(service).collectPostCreated(1L, 2L, "post", occurredAt);
        verify(service).collectCommentCreated(3L, 1L, 2L, "comment", occurredAt);
    }
}
