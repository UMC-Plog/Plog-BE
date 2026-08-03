package com.plog.domain.integration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plog.integration.notion.webhook")
public record NotionWebhookProperties(
        String verificationToken,
        Duration debounce,
        Duration processingTimeout,
        int maxAttempts,
        long pollDelayMs,
        int batchSize
) {
    public NotionWebhookProperties {
        if (debounce == null || debounce.isNegative()) {
            throw new IllegalArgumentException("Notion webhook debounce must not be negative");
        }
        if (processingTimeout == null || processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("Notion webhook processing timeout must be positive");
        }
        if (maxAttempts < 1 || pollDelayMs < 100 || batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Invalid Notion webhook worker configuration");
        }
    }
}
