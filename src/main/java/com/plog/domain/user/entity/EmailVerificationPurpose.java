package com.plog.domain.user.entity;

/**
 * 이메일 인증의 목적. 같은 이메일이 가입 인증과 비밀번호 재설정 인증을 각각 한 행씩 가질 수 있고,
 * 한쪽 목적으로 받은 코드를 다른 목적에 쓸 수 없다.
 */
public enum EmailVerificationPurpose {
    SIGNUP, PASSWORD_RESET
}
