package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 계정 자체(프로필 수정 / 탈퇴)에 대한 에러. 인증·인가 에러는 AuthErrorCode 를 쓴다. */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    NAME_CHANGE_ALREADY_USED(HttpStatus.CONFLICT, "USER001", "실명은 1회만 변경할 수 있습니다."),
    WITHDRAWAL_NOT_AGREED(HttpStatus.BAD_REQUEST, "USER002", "탈퇴 동의가 필요합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
