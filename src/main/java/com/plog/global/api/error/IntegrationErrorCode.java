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
    PROJECT_INTEGRATION_ALREADY_CONNECTED(HttpStatus.CONFLICT, "INTEGRATION011", "이미 연동된 외부 계정입니다. 기존 연동을 해제한 뒤 다시 시도해주세요."),
    PROVIDER_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION012", "외부 provider를 일시적으로 확인할 수 없습니다. 잠시 후 다시 시도해주세요."),
    UNSUPPORTED_GOOGLE_RESOURCE_TYPE(HttpStatus.BAD_REQUEST, "INTEGRATION013", "Google Docs 또는 네이티브 Google Slides 파일만 등록할 수 있습니다."),
    INVALID_EXTERNAL_RESOURCE_URL(HttpStatus.BAD_REQUEST, "INTEGRATION014", "외부 리소스 URL 형식이 올바르지 않습니다."),
    PROVIDER_ACTOR_NOT_FOUND(HttpStatus.NOT_FOUND, "INTEGRATION015", "선택한 provider 계정을 수집 활동에서 찾을 수 없습니다."),
    ACTOR_ALREADY_MAPPED(HttpStatus.CONFLICT, "INTEGRATION016", "이미 다른 프로젝트 멤버에게 매핑된 외부 계정입니다."),
    ACTOR_MAPPING_NOT_FOUND(HttpStatus.NOT_FOUND, "INTEGRATION017", "현재 멤버의 외부 계정 매핑을 찾을 수 없습니다."),
    ACTOR_MAPPING_AMBIGUOUS(HttpStatus.CONFLICT, "INTEGRATION018", "외부 계정 식별값이 여러 프로젝트 멤버의 매핑과 충돌합니다."),
    ACTOR_MAPPING_LOCKED(HttpStatus.BAD_REQUEST, "INTEGRATION019", "최종 제출 후에는 외부 계정 매핑을 변경하거나 해제할 수 없습니다."),
    WORKSPACE_INTEGRATION_LOCKED(HttpStatus.BAD_REQUEST, "INTEGRATION020", "프로젝트 완료 후에는 워크스페이스 연동을 변경할 수 없습니다."),
    GITHUB_RESOURCE_MANAGED_BY_PROVIDER(HttpStatus.BAD_REQUEST, "INTEGRATION021", "GitHub 리소스는 GitHub App 설치 설정에서 관리해야 합니다."),
    GOOGLE_PICKER_TOKEN_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "INTEGRATION024", "Google 계정을 연동한 프로젝트 멤버만 Picker를 사용할 수 있습니다."),
    PROVIDER_REAUTHORIZATION_REQUIRED(HttpStatus.CONFLICT, "INTEGRATION025", "외부 provider 인증이 만료되어 재인증이 필요합니다. 연동을 다시 진행해주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}