package com.plog.domain.user.service;

import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.RefreshToken;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인/토큰 재발급/로그아웃. 모두 DB I/O만 — 외부 I/O 없음.
 * Access는 stateless(서명검증만), Refresh만 DB 조회로 검증한다.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, RefreshTokenService refreshTokenService,
                       JwtProvider jwtProvider, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TokenResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(AuthErrorCode.LOGIN_FAILED));
        // 소셜 유저는 password가 null → matches 호출 전에 차단. 존재/사유 노출 없이 일괄 실패 처리.
        if (user.getPassword() == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new ApiException(AuthErrorCode.LOGIN_FAILED);
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse reissue(String rawRefreshToken) {
        RefreshToken current = refreshTokenService.validateOrThrow(rawRefreshToken);
        User user = current.getUser();
        // 회전: 조회-검증과 소모(삭제)를 분리하면 동시 요청이 함께 통과할 수 있다.
        // 삭제 성공(1행) 여부로 소모를 판정해, 이미 쓰인 토큰이면 재발급을 거부한다.
        if (refreshTokenService.consume(rawRefreshToken) == 0) {
            throw new ApiException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(rawRefreshToken);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = refreshTokenService.issue(user);
        return new TokenResponse(accessToken, refreshToken);
    }
}
