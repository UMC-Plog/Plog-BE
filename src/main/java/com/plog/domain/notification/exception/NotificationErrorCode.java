package com.plog.domain.notification.exception;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 사용자를 찾을 수 없습니다."),
    INVALID_FCM_TOKEN(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "FCM 토큰이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
