package com.plog.domain.user.entity;

/** 소셜 인증 결과. 프론트가 이 값으로 다음 화면(홈 / 약관 동의)을 결정한다. */
public enum SocialLoginStatus {
    LOGIN, SIGNUP_REQUIRED
}
