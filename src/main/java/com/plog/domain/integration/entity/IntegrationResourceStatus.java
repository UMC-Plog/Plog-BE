package com.plog.domain.integration.entity;

/** 연결은 유지하되 개별 리소스에 문제가 생긴 상태를 표현한다. */
public enum IntegrationResourceStatus {
    ACTIVE,
    REAUTH_REQUIRED,
    DISABLED
}
