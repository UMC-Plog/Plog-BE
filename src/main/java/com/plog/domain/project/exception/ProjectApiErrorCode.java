package com.plog.domain.project.exception;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectApiErrorCode implements BaseErrorCode {
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."),
    PROJECT_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "PROJECT_MEMBER_REQUIRED", "활성 프로젝트 멤버만 접근할 수 있습니다."),
    PROJECT_SETTING_PERMISSION_DENIED(
            HttpStatus.FORBIDDEN,
            "PROJECT_SETTING_PERMISSION_DENIED",
            "프로젝트 설정 변경 권한이 없습니다."
    ),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
