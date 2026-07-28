package com.plog.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 이미지 인라인 표시 전용 토큰. Access Token과 <b>다른 키</b>로 서명한다.
 * <p>
 * JwtProvider.parseUserId 는 서명·만료만 보고 클레임을 검사하지 않는다. 같은 키를 쓰면
 * plog_media 쿠키 값을 Authorization 헤더에 옮겨 싣는 것만으로 14일짜리 전 API 토큰이
 * 된다. 키를 분리하면 교차 사용이 서명 검증에서 실패해 검사를 빠뜨릴 여지가 없다
 * (fail-closed). 그래서 JwtProvider 는 이 기능을 위해 수정하지 않는다.
 * <p>
 * 차단은 한 방향이다 — 유효한 Authorization 헤더는 프록시 엔드포인트에도 통한다.
 * 정당하게 인증된 사용자이므로 막을 이유가 없고, Swagger Authorize 가 그대로 동작한다.
 */
@Component
public class MediaTokenProvider {

    private static final String KEY_LABEL = "|media";

    private final SecretKey key;
    private final JwtProperties properties;

    public MediaTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(deriveKeyBytes(properties.secret()));
        this.properties = properties;
    }

    public String createMediaToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.mediaTokenTtl())))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증 후 subject(userId) 반환. 실패 시 JwtException 계열을 던진다. */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw new MalformedJwtException("미디어 토큰 subject가 유효한 사용자 ID 형식이 아닙니다.", exception);
        }
    }

    public Duration ttl() {
        return properties.mediaTokenTtl();
    }

    /**
     * SHA-256 출력이 정확히 32바이트라 HS256 최소 키 길이(256비트)를 만족한다.
     * JwtProperties 가 secret 을 32바이트 이상으로 이미 강제하고 있다.
     */
    private static byte[] deriveKeyBytes(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((secret + KEY_LABEL).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
