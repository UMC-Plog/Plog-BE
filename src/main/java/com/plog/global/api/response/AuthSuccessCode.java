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
    LOGOUT_SUCCESS(HttpStatus.OK, "AUTH007", "로그아웃 되었습니다."),
    PASSWORD_RESET_CODE_SENT(HttpStatus.OK, "AUTH008", "비밀번호 재설정 인증 메일을 발송했습니다."),
    PASSWORD_RESET_EMAIL_VERIFIED(HttpStatus.OK, "AUTH009", "이메일 인증이 완료되었습니다."),
    PASSWORD_RESET_COMPLETED(HttpStatus.OK, "AUTH010", "비밀번호를 재설정했습니다."),

    // 소셜 로그인. 성공/에러 코드 번호가 겹치는 것은 전 도메인 공통 컨벤션이다(isSuccess로 구분).
    SOCIAL_LOGIN_SUCCESS(HttpStatus.OK, "AUTH011", "소셜 로그인에 성공했습니다."),
    SOCIAL_SIGNUP_REQUIRED(HttpStatus.OK, "AUTH012", "추가 정보 입력이 필요합니다."),
    SOCIAL_SIGNUP_COMPLETED(HttpStatus.CREATED, "AUTH013", "회원가입이 완료되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
