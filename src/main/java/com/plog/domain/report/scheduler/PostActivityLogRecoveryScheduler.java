package com.plog.domain.report.scheduler;

import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.report.service.PostActivityLogService;
import com.plog.global.util.TimeUtil;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostActivityLogRecoveryScheduler {

    private static final Limit BATCH = Limit.of(200);
    private static final Duration GRACE = Duration.ofMinutes(5);
    private final ReportActivityLogRepository repository;
    private final PostActivityLogService service;

    @Scheduled(fixedDelay = 300_000)
    public void recollectMissing() {
        var threshold = TimeUtil.nowUtc().minus(GRACE);
        repository.findPostsMissingActivityLog(threshold, BATCH).forEach(target -> {
            try {
                service.collectPostCreated(target.getPostId(), target.getMemberId(),
                        target.getContent(), target.getOccurredAt());
            } catch (RuntimeException exception) {
                log.warn("post_activity_log_recovery_failed postId={}", target.getPostId(), exception);
            }
        });
        repository.findCommentsMissingActivityLog(threshold, BATCH).forEach(target -> {
            try {
                service.collectCommentCreated(target.getCommentId(), target.getPostId(), target.getMemberId(),
                        target.getContent(), target.getOccurredAt());
            } catch (RuntimeException exception) {
                log.warn("comment_activity_log_recovery_failed commentId={}", target.getCommentId(), exception);
            }
        });
    }
}
