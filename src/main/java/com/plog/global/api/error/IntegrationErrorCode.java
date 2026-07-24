package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum IntegrationErrorCode implements BaseErrorCode {

    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "INTEGRATION001", "지원하지 않는 외부 연동 provider입니다."),
    AUTHORIZATION_STATE_INVALID(HttpStatus.BAD_REQUEST, "INTEGRATION002", "유효하지 않은 연동 요청입니다."),
    AUTHORIZATION_STATE_EXPIRED(HttpStatus.BAD_REQUEST, "INTEGRATION003", "연동 요청이 만료되었습니다. 다시 시도해주세요."),
    PROVIDER_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTEGRATION004", "외부 연동 설정이 올바르지 않습니다."),
    PROVIDER_AUTHORIZATION_FAILED(HttpStatus.BAD_GATEWAY, "INTEGRATION005", "외부 provider 인증에 실패했습니다."),
    PROJECT_INTEGRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "INTEGRATION006", "프로젝트 외부 연동을 찾을 수 없습니다."),
    EXTERNAL_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "INTEGRATION007", "외부 리소스를 찾을 수 없습니다."),
    EXTERNAL_RESOURCE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "INTEGRATION008", "이미 등록된 외부 리소스입니다."),
    CREDENTIAL_ENCRYPTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTEGRATION009", "외부 연동 자격증명을 처리할 수 없습니다."),
    PROVIDER_RESOURCE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "INTEGRATION010", "외부 리소스 접근 권한이 없습니다."),
    PROJECT_INTEGRATION_ALREADY_CONNECTED(HttpStatus.CONFLICT, "INTEGRATION011", "이미 연동된 외부 계정입니다. 기존 연동을 해제한 뒤 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
