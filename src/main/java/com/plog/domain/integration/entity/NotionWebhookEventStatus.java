package com.plog.domain.integration.entity;

/** Notion Webhook 이벤트의 DB 큐 처리 상태다. */
public enum NotionWebhookEventStatus {
    PENDING,
    PROCESSING,
    RETRYABLE,
    SUCCEEDED,
    PARTIAL_FAILED,
    IGNORED,
    FAILED,
    REAUTH_REQUIRED
}
