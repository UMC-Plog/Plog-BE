package com.plog.domain.report.repository.projection;

import java.time.LocalDateTime;

/**
 * TASK_STATUS_CHANGE(DONE 전이) 재수집 대상. DONE 전이만 대상인 이유는
 * {@code TaskActivityLogRecoveryScheduler} 문서 참고 — Task에 완료 시각(completedAt) 말고는
 * 신뢰 가능한 전이 시각 컬럼이 없다.
 */
public interface TaskStatusLogRecoveryTarget {
    Long getTaskId();

    Long getMemberId();

    LocalDateTime getOccurredAt();
}