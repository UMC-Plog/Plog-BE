package com.plog.domain.integration.service;

import java.time.Duration;
import org.springframework.stereotype.Component;

/** Notion의 integration별 평균 3 req/s 제한을 넘지 않도록 수집 호출을 직렬 조절한다. */
@Component
class NotionApiRateLimiter {

    private static final long MIN_INTERVAL_NANOS = Duration.ofMillis(350).toNanos();

    private long nextRequestAtNanos;

    synchronized void acquire() {
        long now = System.nanoTime();
        long waitNanos = nextRequestAtNanos - now;
        if (waitNanos > 0) {
            try {
                long millis = waitNanos / 1_000_000L;
                int nanos = (int) (waitNanos % 1_000_000L);
                Thread.sleep(millis, nanos);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Notion API rate-limit wait was interrupted", exception);
            }
        }
        nextRequestAtNanos = System.nanoTime() + MIN_INTERVAL_NANOS;
    }
}
