package com.plog.domain.user.entity;

/** 지원하는 소셜 로그인 제공자. 값을 추가할 때는 SocialOAuthClient 구현체도 함께 추가한다. */
public enum ProviderType {
    KAKAO, GOOGLE
}
