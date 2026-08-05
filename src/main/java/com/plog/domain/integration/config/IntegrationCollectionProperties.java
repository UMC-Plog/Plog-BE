package com.plog.domain.integration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plog.integration.collection")
public record IntegrationCollectionProperties(
        long pollDelayMs,
        int batchSize,
        Duration processingTimeout,
        int maxAttempts,
        int cursorFlushInterval,
        Duration watermarkOverlap,
        long githubMinIntervalMs,
        int rateLimitMinRemaining
) {
    public IntegrationCollectionProperties {
        if (processingTimeout == null || processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("Integration collection processing timeout must be positive");
        }
        if (watermarkOverlap == null || watermarkOverlap.isNegative()) {
            throw new IllegalArgumentException("Integration collection watermark overlap must not be negative");
        }
        if (pollDelayMs < 100 || batchSize < 1 || batchSize > 100 || maxAttempts < 1) {
            throw new IllegalArgumentException("Invalid integration collection worker configuration");
        }
        if (cursorFlushInterval < 1 || githubMinIntervalMs < 0 || rateLimitMinRemaining < 0) {
            throw new IllegalArgumentException("Invalid integration collection tuning configuration");
        }
    }
}
