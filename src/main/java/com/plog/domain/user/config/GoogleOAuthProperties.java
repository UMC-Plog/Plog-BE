package com.plog.domain.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * plog.oauth.google.* 바인딩.
 * plog.integration.google(Drive 연동용)과 반드시 다른 OAuth 클라이언트를 쓴다 —
 * 연동용은 민감 스코프라 향후 구글 검수 대상이 될 수 있고, 그 심사가 로그인까지 묶이면 안 된다.
 */
@ConfigurationProperties(prefix = "plog.oauth.google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri
) {}
