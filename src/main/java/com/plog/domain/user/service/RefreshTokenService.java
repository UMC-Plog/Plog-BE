package com.plog.domain.user.service;

import com.plog.domain.user.entity.RefreshToken;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.RefreshTokenRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProperties;
import com.plog.global.util.HashUtil;
import com.plog.global.util.TimeUtil;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰 발급/검증/폐기. 토큰 원문은 클라이언트에만 존재하고 DB엔 SHA-256 해시만 남는다.
 * 원문은 self-contained JWT가 아니라 불투명 랜덤값 — 검증을 어차피 DB 조회로 하기 때문(회전/폐기 가능).
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32; // 256-bit 엔트로피

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties,
                               RefreshTokenRevoker refreshTokenRevoker) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.refreshTokenRevoker = refreshTokenRevoker;
    }

    /** 새 리프레시 토큰을 발급·저장하고 원문을 반환한다(원문은 이 순간에만 존재). */
    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        LocalDateTime expiresAt = TimeUtil.now().plus(jwtProperties.refreshTokenTtl());
        refreshTokenRepository.save(RefreshToken.issue(user, HashUtil.sha256Hex(rawToken), expiresAt));
        log.info("refresh_issue 발급 fp={} userId={}", HashUtil.fingerprint(rawToken), user.getId());
        return rawToken;
    }

    /**
     * 회전. 첫 사용이면 소모 표시하고 소유자를 돌려준다.
     * <p>
     * 예전에는 소모가 곧 DELETE 라, 같은 원문이 두 번 들어오면 나중 것이 무조건 실패해 로그아웃됐다.
     * 홈 화면에 추가한 PWA 는 스와이프로 종료할 때마다 콜드 스타트라 부팅 재발급이 매번 돌고,
     * 그 요청이 겹치거나(멀티탭·병렬 401 재시도) 모바일 네트워크에서 재전송되면 그 조건이 상시 성립한다.
     * 그래서 유예 안의 재사용은 재시도로 보고 통과시키고, 유예를 넘긴 재사용만 탈취로 처리한다.
     */
    @Transactional
    public User rotateOrThrow(String rawToken) {
        String tokenHash = HashUtil.sha256Hex(rawToken);
        String fingerprint = HashUtil.fingerprint(rawToken);
        LocalDateTime now = TimeUtil.now();

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("refresh_rotate 거부: 알 수 없는 토큰 fp={}", fingerprint);
                    return new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
                });
        if (token.isExpired(now)) {
            log.warn("refresh_rotate 거부: 만료 fp={} userId={} expiresAt={}",
                    fingerprint, token.getUser().getId(), token.getExpiresAt());
            throw new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        // 1을 받은 요청만 첫 사용이다. 동시 요청은 행 잠금에서 순서가 갈려 하나만 1을 받는다.
        if (refreshTokenRepository.markUsed(tokenHash, now) == 1) {
            log.info("refresh_rotate 첫 사용 fp={} userId={}", fingerprint, token.getUser().getId());
            return token.getUser();
        }

        // 이미 소모된 토큰. 소모 시각으로 재시도와 탈취를 가른다.
        // 여기서 엔티티(token)의 usedAt 을 믿으면 안 된다 — 위 조회 시점의 값이라 방금 커밋된 소모를 못 본다.
        LocalDateTime usedAt = refreshTokenRepository.findUsedAtByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("refresh_rotate 거부: 판정 중 토큰이 사라짐 fp={}", fingerprint);
                    return new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
                });
        long elapsed = Duration.between(usedAt, now).toSeconds();
        if (usedAt.plus(jwtProperties.refreshTokenGrace()).isBefore(now)) {
            // 한참 전에 쓴 토큰이 다시 나타났다 = 원문이 샜다고 본다.
            // 새 토큰만 거부하면 탈취자가 회전을 이어가므로 이 유저의 세션을 전부 끊는다.
            log.error("refresh_rotate 재사용 탐지 — 해당 유저 세션 전체 폐기 fp={} userId={} 소모후={}초",
                    fingerprint, token.getUser().getId(), elapsed);
            // 바로 아래 예외가 이 트랜잭션을 롤백시키므로 폐기는 별도 트랜잭션으로 내보낸다.
            refreshTokenRevoker.revokeAllSessions(token.getUser().getId());
            throw new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        // 이 줄이 찍히면 클라이언트가 같은 토큰으로 재발급을 두 번 보냈다는 뜻이다(부팅 중복 호출·네트워크 재시도).
        // 예전 구현이라면 여기서 로그아웃됐다.
        log.warn("refresh_rotate 유예 내 재시도 허용 fp={} userId={} 소모후={}초",
                fingerprint, token.getUser().getId(), elapsed);
        return token.getUser();
    }

    /**
     * 로그아웃: 원문에 해당하는 토큰 폐기. 없어도 조용히 통과(멱등).
     * <p>
     * 이 로그가 사용자가 로그아웃을 누르지 않은 시점에 찍힌다면, 클라이언트가 화면 종료 훅
     * (pagehide/visibilitychange 등)에서 로그아웃을 부르고 있다는 뜻이다.
     */
    @Transactional
    public void revokeByRawToken(String rawToken) {
        int revoked = refreshTokenRepository.deleteByTokenHash(HashUtil.sha256Hex(rawToken));
        log.info("refresh_revoke 로그아웃 fp={} 폐기={}건", HashUtil.fingerprint(rawToken), revoked);
    }

    /**
     * 만료됐거나 유예까지 끝난 토큰을 지운다.
     * 회전이 DELETE 대신 표시만 하므로, 이 정리가 없으면 refresh_token 이 계속 커진다.
     */
    @Transactional
    public int purgeExhausted() {
        LocalDateTime now = TimeUtil.now();
        return refreshTokenRepository.deleteExhausted(now, now.minus(jwtProperties.refreshTokenGrace()));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
