package com.plog.domain.integration.service;

/** provider가 반환한 리소스 단위 HTTP 실패를 수집 상태 전이에 사용한다. */
class ProviderResourceAccessException extends RuntimeException {

    private final int statusCode;

    ProviderResourceAccessException(int statusCode, Throwable cause) {
        super(cause);
        this.statusCode = statusCode;
    }

    int statusCode() {
        return statusCode;
    }
}
