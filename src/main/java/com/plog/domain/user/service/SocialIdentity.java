package com.plog.domain.user.service;

import com.plog.domain.user.entity.ProviderType;

/**
 * provider 응답에서 뽑아낸, 검증이 끝난 소셜 신원.
 * provider마다 다른 응답 형태를 여기서 흡수해 서비스 계층이 provider를 몰라도 되게 한다.
 */
public record SocialIdentity(ProviderType provider, String providerId, String email) {
}
