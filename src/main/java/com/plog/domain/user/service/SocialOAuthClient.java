package com.plog.domain.user.service;

import com.plog.domain.user.entity.ProviderType;

/**
 * provider별 인가 코드 교환. 구현체는 외부 HTTP를 호출하므로 트랜잭션 밖에서 부른다.
 * 이메일을 얻지 못하거나 provider가 미검증으로 표시하면 SOCIAL_EMAIL_NOT_PROVIDED 로 끊는다 —
 * 미검증 이메일을 신뢰하면 uk_user_email 이 계정 식별에 관여하는 구조상 남의 주소로 계정을 만들 수 있다.
 */
public interface SocialOAuthClient {

    ProviderType provider();

    SocialIdentity exchange(String code);
}
