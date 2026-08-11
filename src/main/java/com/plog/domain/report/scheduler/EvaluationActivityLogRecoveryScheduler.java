package com.plog.domain.report.scheduler;

import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.report.repository.projection.EvaluationLogRecoveryTarget;
import com.plog.domain.report.service.EvaluationActivityLogService;
import com.plog.global.util.TimeUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 평가 활동 로그 수집의 안전망. 정상 경로는 {@code EvaluationActivityLogListener}의
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT)}지만, 이 조합은 전달을 보장하지 못한다 —
 * 원본 트랜잭션이 커밋된 뒤 비동기 소비가 실패하거나(예외) 리스너가 돌기 전에 프로세스가 죽으면
 * {@code ReportActivityLog}가 영영 생성되지 않는다. 썸네일 아웃박스(ThumbnailScheduler)와 같은
 * 판단으로, DB 자체를 큐 삼아 아직 로그가 없는 제출을 주기적으로 재수집한다.
 * <p>
 * 재수집이 안전한 이유는 {@link EvaluationActivityLogService}의 수집이 멱등하기 때문이다
 * (source advisory lock + existsBySourceDomainAndSourceRefId). 정상 경로와 겹치거나 인스턴스가
 * 여러 대라 배치가 중복 실행돼도 로그는 한 번만 적재된다. 그래도 정상 처리 중인 건과 무의미하게
 * 부딪히지 않도록 {@link #GRACE}가 지난 행만 대상으로 삼는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationActivityLogRecoveryScheduler {

    private static final Limit BATCH = Limit.of(200);
    /** 비동기 리스너는 보통 수십 ms 안에 끝난다. 이 시간이 지나도 로그가 없으면 유실로 보고 재수집한다. */
    private static final Duration GRACE = Duration.ofMinutes(5);

    private final ReportActivityLogRepository activityLogRepository;
    private final EvaluationActivityLogService activityLogService;

    @Scheduled(fixedDelay = 300_000)
    public void recollectMissing() {
        LocalDateTime threshold = TimeUtil.now().minus(GRACE);
        recollectPeerEvaluations(threshold);
        recollectSelfFeedbacks(threshold);
    }

    private void recollectPeerEvaluations(LocalDateTime threshold) {
        List<EvaluationLogRecoveryTarget> targets =
                activityLogRepository.findPeerEvaluationsMissingActivityLog(threshold, BATCH);
        for (EvaluationLogRecoveryTarget target : targets) {
            // 한 건의 실패가 배치 전체를 막지 않도록 건별로 격리한다(collect는 REQUIRES_NEW).
            try {
                activityLogService.collectPeerEvaluation(target.getId(), target.getOccurredAt());
                log.info("peer_evaluation_activity_log_recovered evaluationId={}", target.getId());
            } catch (RuntimeException e) {
                log.warn("peer_evaluation_activity_log_recovery_failed evaluationId={}", target.getId(), e);
            }
        }
    }

    private void recollectSelfFeedbacks(LocalDateTime threshold) {
        List<EvaluationLogRecoveryTarget> targets =
                activityLogRepository.findSelfFeedbacksMissingActivityLog(threshold, BATCH);
        for (EvaluationLogRecoveryTarget target : targets) {
            try {
                activityLogService.collectSelfFeedback(target.getId(), target.getOccurredAt());
                log.info("self_feedback_activity_log_recovered selfFeedbackId={}", target.getId());
            } catch (RuntimeException e) {
                log.warn("self_feedback_activity_log_recovery_failed selfFeedbackId={}", target.getId(), e);
            }
        }
    }
}
