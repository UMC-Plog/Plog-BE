package com.plog.domain.user.service;

import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
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
    private final TokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, RefreshTokenService refreshTokenService,
                       TokenIssuer tokenIssuer, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.tokenIssuer = tokenIssuer;
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
        // 비밀번호 검증 뒤에 두는 이유: 순서를 뒤집으면 비밀번호를 모르는 사람도
        // "이 이메일은 탈퇴 중"이라는 정보를 얻는다.
        if (user.isWithdrawn()) {
            throw new ApiException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }
        return tokenIssuer.issue(user);
    }

    @Transactional
    public TokenResponse reissue(String rawRefreshToken) {
        // 회전·재시도 허용·탈취 판정은 전부 RefreshTokenService 안에 있다(원문을 다루는 곳을 한 군데로 모은다).
        User user = refreshTokenService.rotateOrThrow(rawRefreshToken);
        // 탈퇴는 리프레시 토큰을 전부 지우지만(UserWithdrawalService), 그 일괄 삭제 직후에 이 재발급이
        // 새 토큰을 넣으면 죽은 계정에 살아있는 토큰이 남아 이후로도 계속 갱신된다.
        // 로그인과 달리 여기선 계정 존재 노출을 걱정할 필요가 없다 — 이미 유효한 리프레시 토큰 소지자다.
        if (user.isWithdrawn()) {
            throw new ApiException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }
        return tokenIssuer.issue(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(rawRefreshToken);
    }
}
