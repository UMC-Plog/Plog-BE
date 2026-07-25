package com.plog.domain.integration.service;

/** 리소스 등록·동기화 전에만 사용하는 provider 권한 확인 결과다. */
public enum IntegrationVerificationStatus {
    NOT_CONNECTED,
    VERIFIED,
    DISCONNECTED,
    UNAVAILABLE
}
