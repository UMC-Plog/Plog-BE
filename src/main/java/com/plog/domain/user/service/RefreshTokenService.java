package com.plog.domain.user.service;

import com.plog.domain.user.entity.RefreshToken;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.RefreshTokenRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProperties;
import com.plog.global.util.HashUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰 발급/검증/폐기. 토큰 원문은 클라이언트에만 존재하고 DB엔 SHA-256 해시만 남는다.
 * 원문은 self-contained JWT가 아니라 불투명 랜덤값 — 검증을 어차피 DB 조회로 하기 때문(회전/폐기 가능).
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32; // 256-bit 엔트로피

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    /** 새 리프레시 토큰을 발급·저장하고 원문을 반환한다(원문은 이 순간에만 존재). */
    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProperties.refreshTokenTtl());
        refreshTokenRepository.save(RefreshToken.issue(user, HashUtil.sha256Hex(rawToken), expiresAt));
        return rawToken;
    }

    /** 원문을 검증해 엔티티를 반환. 없거나 만료면 INVALID_REFRESH_TOKEN. */
    @Transactional(readOnly = true)
    public RefreshToken validateOrThrow(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        if (token.isExpired(LocalDateTime.now())) {
            throw new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        return token;
    }

    /**
     * 회전(rotation): 원문에 해당하는 토큰을 원자적으로 소모(삭제)하고 삭제된 행 수를 반환한다.
     * 동시 요청이 같은 토큰으로 들어와도 단 하나만 1을 받고 나머지는 0을 받으므로, 반환값으로 소모 성공을 판정한다.
     */
    @Transactional
    public int consume(String rawToken) {
        return refreshTokenRepository.deleteByTokenHash(HashUtil.sha256Hex(rawToken));
    }

    /** 로그아웃: 원문에 해당하는 토큰 폐기. 없어도 조용히 통과(멱등). */
    @Transactional
    public void revokeByRawToken(String rawToken) {
        refreshTokenRepository.deleteByTokenHash(HashUtil.sha256Hex(rawToken));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
