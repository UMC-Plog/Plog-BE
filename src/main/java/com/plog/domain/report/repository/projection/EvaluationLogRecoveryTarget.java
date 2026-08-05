package com.plog.domain.report.repository.projection;

import java.time.LocalDateTime;

/**
 * AFTER_COMMIT 리스너가 유실한(비동기 소비 실패·커밋 직후 프로세스 종료) 평가 제출을
 * 재수집하기 위한 안전망 조회 결과. 원본 엔티티 id와, 원래 이벤트의 occurredAt 대용으로
 * 쓰는 생성 시각을 담는다.
 */
public interface EvaluationLogRecoveryTarget {
    Long getId();

    LocalDateTime getOccurredAt();
}
