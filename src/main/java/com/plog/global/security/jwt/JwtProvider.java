package com.plog.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Access Token 발급/검증. stateless — 서명·만료 검증만 하고 DB를 조회하지 않는다.
 * (Refresh Token은 별도로 DB 해시 조회로 검증 — Step 4)
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtProvider(JwtProperties properties) {
        // 약한 키면 hmacShaKeyFor 가 WeakKeyException 을 던져 기동 실패 (JwtProperties에서도 1차 검증)
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
    }

    public String createAccessToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증 후 subject(userId) 반환. 실패 시 JwtException 계열(ExpiredJwtException 포함)을 던진다. */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
