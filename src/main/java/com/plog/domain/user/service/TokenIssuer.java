package com.plog.domain.user.service;

import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.User;
import com.plog.global.security.jwt.JwtProvider;
import org.springframework.stereotype.Service;

/**
 * Access + Refresh 토큰 발급. 로그인·재발급·소셜 로그인·소셜 가입 네 경로가 같은 절차를 쓴다.
 * 한 곳에 모아 "발급 시 무엇을 함께 해야 하는가"가 갈라지지 않게 한다.
 */
@Service
public class TokenIssuer {

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public TokenIssuer(JwtProvider jwtProvider, RefreshTokenService refreshTokenService) {
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    public TokenResponse issue(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = refreshTokenService.issue(user);
        return new TokenResponse(accessToken, refreshToken);
    }
}
