package com.plog.global.api.response;

import com.plog.global.api.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements BaseCode {

    EMAIL_CODE_SENT(HttpStatus.OK, "AUTH001", "인증 메일을 발송했습니다."),
    EMAIL_VERIFIED(HttpStatus.OK, "AUTH002", "이메일 인증이 완료되었습니다."),
    NICKNAME_AVAILABLE(HttpStatus.OK, "AUTH003", "사용 가능한 닉네임입니다."),
    SIGNUP_COMPLETED(HttpStatus.CREATED, "AUTH004", "회원가입이 완료되었습니다."),
    LOGIN_SUCCESS(HttpStatus.OK, "AUTH005", "로그인에 성공했습니다."),
    TOKEN_REISSUED(HttpStatus.OK, "AUTH006", "토큰이 재발급되었습니다."),
    LOGOUT_SUCCESS(HttpStatus.OK, "AUTH007", "로그아웃 되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
