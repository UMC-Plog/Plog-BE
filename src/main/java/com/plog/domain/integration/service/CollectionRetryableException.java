package com.plog.domain.integration.service;

import java.time.Instant;

/**
 * 수집을 지금 이어갈 수 없지만 실패는 아님을 알린다. 워커가 커서를 저장하고 잡을 재큐한다.
 *
 * <p>두 경우에 쓴다.</p>
 * <ul>
 *   <li>rate limit 예산이 임계치 아래로 떨어짐 — 남은 예산을 태우고 403을 맞느니 미리 멈춘다.</li>
 *   <li>provider가 일시 장애를 반환 — 리포지토리 동기화 실패 등.</li>
 * </ul>
 *
 * <p>{@code nextAttemptAt}이 null이면 워커가 지수 백오프로 정한다.</p>
 */
class CollectionRetryableException extends RuntimeException {

    private final Instant nextAttemptAt;

    CollectionRetryableException(String reason, Instant nextAttemptAt) {
        super(reason);
        this.nextAttemptAt = nextAttemptAt;
    }

    Instant nextAttemptAt() {
        return nextAttemptAt;
    }
}
