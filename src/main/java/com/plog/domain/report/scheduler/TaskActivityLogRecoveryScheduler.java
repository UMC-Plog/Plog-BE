package com.plog.domain.report.scheduler;

import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.report.repository.projection.TaskAttachmentLogRecoveryTarget;
import com.plog.domain.report.repository.projection.TaskStatusLogRecoveryTarget;
import com.plog.domain.report.service.TaskActivityLogService;
import com.plog.domain.task.entity.TaskStatus;
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
 * Task 활동 로그 수집의 안전망. 정상 경로는 {@code TaskActivityLogListener}의
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT)}지만, 이 조합은 전달을 보장하지 못한다 —
 * 원본(Task 상태변경/첨부 등록) 트랜잭션은 이미 커밋됐는데, 그 뒤 비동기 소비가 실패하거나
 * (예외) 리스너가 돌기 전에 프로세스가 죽으면 {@code ReportActivityLog}가 영영 생성되지 않는다.
 * {@code REQUIRES_NEW}와 source lock은 같은 이벤트가 두 번 들어왔을 때 중복 적재만 막을 뿐,
 * 애초에 한 번도 적재되지 않은 유실은 막지 못한다.
 * {@code EvaluationActivityLogRecoveryScheduler}와 같은 판단으로, DB 자체를 큐 삼아 아직 로그가
 * 없는 원본을 주기적으로 재수집한다.
 * <p>
 * <b>범위: TASK_STATUS_CHANGE는 DONE 전이만 재수집한다.</b> Task에는 "완료 시각"(completedAt)만
 * 있고 그 외 상태 전이(TODO↔IN_PROGRESS)의 발생 시각을 담는 컬럼이 없다 — updatedAt은 상태 변경이
 * 아닌 다른 필드 수정에도 갱신되므로 신뢰할 수 없다. 완료 처리가 PM 명세상으로도 가장 중요한
 * 신호라 이 범위로도 실질적인 안전망이 된다. TASK_ATTACHMENT_ADD는 전이 개념이 없어 전부 대상이다.
 * <p>
 * 재수집이 안전한 이유는 {@link TaskActivityLogService}의 수집이 멱등하기 때문이다
 * (source advisory lock + existsBySourceDomainAndSourceRefId). 정상 경로와 겹치거나 인스턴스가
 * 여러 대라 배치가 중복 실행돼도 로그는 한 번만 적재된다. 그래도 정상 처리 중인 건과 무의미하게
 * 부딪히지 않도록 {@link #GRACE}가 지난 행만 대상으로 삼는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskActivityLogRecoveryScheduler {

    private static final Limit BATCH = Limit.of(200);
    /** 비동기 리스너는 보통 수십 ms 안에 끝난다. 이 시간이 지나도 로그가 없으면 유실로 보고 재수집한다. */
    private static final Duration GRACE = Duration.ofMinutes(5);

    private final ReportActivityLogRepository activityLogRepository;
    private final TaskActivityLogService activityLogService;

    @Scheduled(fixedDelay = 300_000)
    public void recollectMissing() {
        LocalDateTime threshold = TimeUtil.nowUtc().minus(GRACE);
        recollectStatusChanges(threshold);
        recollectAttachments(threshold);
    }

    private void recollectStatusChanges(LocalDateTime threshold) {
        List<TaskStatusLogRecoveryTarget> targets =
                activityLogRepository.findDoneTasksMissingActivityLog(threshold, BATCH);
        for (TaskStatusLogRecoveryTarget target : targets) {
            // 한 건의 실패가 배치 전체를 막지 않도록 건별로 격리한다(collect는 REQUIRES_NEW).
            // previousStatus는 null — Task가 상태 이력을 저장하지 않아 재수집 경로는 알 수 없다
            // (TaskStatusChangeMetadata 참고).
            try {
                activityLogService.collectStatusChanged(
                        target.getTaskId(), target.getMemberId(), null, TaskStatus.DONE, target.getOccurredAt());
                log.info("task_status_activity_log_recovered taskId={}", target.getTaskId());
            } catch (RuntimeException e) {
                log.warn("task_status_activity_log_recovery_failed taskId={}", target.getTaskId(), e);
            }
        }
    }

    private void recollectAttachments(LocalDateTime threshold) {
        List<TaskAttachmentLogRecoveryTarget> targets =
                activityLogRepository.findAttachmentsMissingActivityLog(threshold, BATCH);
        for (TaskAttachmentLogRecoveryTarget target : targets) {
            try {
                activityLogService.collectAttachmentAdded(
                        target.getAttachmentId(), target.getTaskId(), target.getMemberId(),
                        target.getOccurredAt());
                log.info("task_attachment_activity_log_recovered attachmentId={}", target.getAttachmentId());
            } catch (RuntimeException e) {
                log.warn("task_attachment_activity_log_recovery_failed attachmentId={}",
                        target.getAttachmentId(), e);
            }
        }
    }
}