package com.plog.domain.integration.entity;

/** Plog 프로젝트와 provider 계정 사이 연결의 현재 상태다. */
public enum IntegrationConnectionStatus {
    ACTIVE,
    REAUTH_REQUIRED,
    /** Provider 권한은 유지한 채 Plog 프로젝트 내부 연결만 해제된 상태다. */
    REVOKED
}
