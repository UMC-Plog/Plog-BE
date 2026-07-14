package com.plog.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.jwt.* 바인딩. secret 기본값 없음 → 환경변수 누락 시 바인딩 실패로 기동 중단.
 * HS256 최소 키 길이(256비트 = UTF-8 32바이트)를 생성 시점에 검증한다.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
    private static final int MIN_SECRET_BYTES = 32; // 256 bit

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret 은 HS256을 위해 최소 " + MIN_SECRET_BYTES + "바이트(256비트) 이상이어야 합니다.");
        }
        if (accessTokenTtl == null || refreshTokenTtl == null) {
            throw new IllegalStateException("app.jwt.access-token-ttl / refresh-token-ttl 이 필요합니다.");
        }
    }
}
