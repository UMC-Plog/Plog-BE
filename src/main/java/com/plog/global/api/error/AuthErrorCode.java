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
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH013", "유효하지 않은 리프레시 토큰입니다."),

    // 비밀번호 재설정: 가입 흐름의 EMAIL_DUPLICATED_* 와 정반대 방향의 검사다.
    EMAIL_NOT_REGISTERED(HttpStatus.NOT_FOUND, "AUTH014", "가입되지 않은 이메일입니다."),
    SOCIAL_PASSWORD_RESET_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "AUTH015",
            "소셜 계정은 비밀번호를 재설정할 수 없습니다. 소셜 로그인을 이용해 주세요."),
    // 탈퇴 유예기간 중. 로그인/재설정에서 사용.
    WITHDRAWN_ACCOUNT(HttpStatus.FORBIDDEN, "AUTH016", "탈퇴 처리 중인 계정입니다."),
    // 탈퇴 유예기간 중 같은 이메일 재가입 시도.
    EMAIL_WITHDRAWAL_PENDING(HttpStatus.CONFLICT, "AUTH017",
            "탈퇴 처리 중인 이메일입니다. 잠시 후 다시 가입할 수 있습니다."),
    // 비밀번호 재설정 화면의 "비밀번호 확인" 칸 불일치.
    // 형식 오류(COMMON400)와 코드를 나눠, 프론트가 어느 입력칸에 에러를 띄울지 응답만 보고 판단할 수 있게 한다.
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH018",
            "비밀번호와 비밀번호 확인이 일치하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
