package com.plog.domain.integration.entity;

/** 외부 리소스별 최근 수집 상태다. */
public enum IntegrationCollectionStatus {
    NOT_STARTED,
    PENDING,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    PARTIAL_FAILED,
    FAILED,
    REAUTH_REQUIRED
}
