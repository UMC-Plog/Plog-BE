package com.plog.global.api.error;

import com.plog.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    // 회원가입 / 계정
    EMAIL_DUPLICATED_LOCAL(HttpStatus.CONFLICT, "AUTH001", "이미 사용 중인 이메일입니다."),
    // 계정 존재 + 가입 수단(provider) 노출은 와이어프레임(유가입자 모달) 요구에 따른 의도된 정책
    EMAIL_DUPLICATED_SOCIAL(HttpStatus.CONFLICT, "AUTH002", "이미 소셜 계정으로 가입된 이메일입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "AUTH003", "이미 사용 중인 닉네임입니다."),
    REQUIRED_AGREEMENT_MISSING(HttpStatus.BAD_REQUEST, "AUTH004", "필수 약관에 동의해야 합니다."),

    // 이메일 인증
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH005", "인증 코드가 일치하지 않습니다."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH006", "인증 코드가 만료되었습니다."),
    VERIFICATION_ATTEMPT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH007", "인증 시도 횟수를 초과했습니다. 코드를 다시 발급받아 주세요."),
    VERIFICATION_RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH008", "잠시 후 다시 시도해 주세요."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH009", "이메일 인증이 필요합니다."),

    // 로그인 / 토큰
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH010", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH011", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH012", "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH013", "유효하지 않은 리프레시 토큰입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
