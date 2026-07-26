package com.plog.domain.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * plog.oauth.kakao.* 바인딩. clientId는 카카오 REST API 키다(JavaScript 키 아님).
 * redirectUri는 프론트가 인가 요청에 싣는 값과 완전히 같아야 한다 — 다르면 카카오가 KOE006으로 거부한다.
 * 값 누락은 기동이 아니라 호출 시점에 AUTH024로 드러난다(provider 연결 전에도 앱이 떠야 한다).
 */
@ConfigurationProperties(prefix = "plog.oauth.kakao")
public record KakaoOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri
) {}
