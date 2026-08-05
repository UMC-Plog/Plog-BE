package com.plog.domain.integration.entity;

/** 수동 수집 잡의 수명 주기. PENDING/RUNNING/RETRYABLE이 활성, 나머지가 종료 상태다. */
public enum IntegrationCollectionJobStatus {
    PENDING,
    RUNNING,
    RETRYABLE,
    SUCCEEDED,
    PARTIAL_FAILED,
    FAILED
}
