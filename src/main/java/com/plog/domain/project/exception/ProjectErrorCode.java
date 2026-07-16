package com.plog.domain.project.exception;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectErrorCode implements BaseErrorCode {
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."),
    PROJECT_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "PROJECT_MEMBER_REQUIRED", "활성 프로젝트 멤버만 접근할 수 있습니다."),
    PROJECT_SETTING_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "PROJECT_SETTING_PERMISSION_DENIED", "프로젝트 설정 변경 권한이 없습니다."),
    INVALID_PROJECT_NAME(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "프로젝트명은 2자 이상 20자 이하여야 합니다."),
    INVALID_PROJECT_END_DAY(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "예상 종료일은 오늘과 시작일 이후여야 합니다."),
    INVITE_TOKEN_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INVITE_TOKEN_CONFIGURATION_ERROR", "초대 토큰 설정이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
