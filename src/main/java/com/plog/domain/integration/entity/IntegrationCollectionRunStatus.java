package com.plog.domain.integration.entity;

public enum IntegrationCollectionRunStatus {
    PENDING,
    RUNNING,
    RETRYABLE,
    SUCCEEDED,
    PARTIAL_FAILED
}
