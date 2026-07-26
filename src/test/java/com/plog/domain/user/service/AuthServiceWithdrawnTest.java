package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.RefreshToken;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceWithdrawnTest {

    @Test
    @DisplayName("탈퇴 처리 중인 계정은 로그인할 수 없다")
    void withdrawnUserCannotLogin() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        TokenIssuer tokenIssuer = mock(TokenIssuer.class);

        User user = User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
        user.withdraw(LocalDateTime.of(2026, 7, 25, 12, 0));
        given(userRepository.findByEmail("a@plog.test")).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

        AuthService authService =
                new AuthService(userRepository, refreshTokenService, tokenIssuer, passwordEncoder);

        assertThatThrownBy(() -> authService.login("a@plog.test", "plog1234"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.WITHDRAWN_ACCOUNT);
    }

    @Test
    @DisplayName("탈퇴 처리 중인 계정은 리프레시 토큰이 살아남아 있어도 재발급받을 수 없다")
    void withdrawnUserCannotReissue() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        TokenIssuer tokenIssuer = mock(TokenIssuer.class);

        User user = User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
        user.withdraw(LocalDateTime.of(2026, 7, 25, 12, 0));
        // 탈퇴가 토큰을 전부 지운 직후에 진행 중이던 재발급이 새 토큰을 넣으면 이런 행이 남는다.
        RefreshToken survivingToken = RefreshToken.issue(
                user, "hash", LocalDateTime.of(2026, 8, 25, 12, 0));
        given(refreshTokenService.validateOrThrow("raw-token")).willReturn(survivingToken);
        given(refreshTokenService.consume("raw-token")).willReturn(1);

        AuthService authService =
                new AuthService(userRepository, refreshTokenService, tokenIssuer, passwordEncoder);

        assertThatThrownBy(() -> authService.reissue("raw-token"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.WITHDRAWN_ACCOUNT);

        // 여기서 막지 않으면 죽은 계정이 토큰을 무기한 갱신한다.
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    @DisplayName("신규 에러 코드가 AUTH014~017로 정의되어 있다")
    void newErrorCodes() {
        assertThat(AuthErrorCode.EMAIL_NOT_REGISTERED.getCode()).isEqualTo("AUTH014");
        assertThat(AuthErrorCode.SOCIAL_PASSWORD_RESET_NOT_ALLOWED.getCode()).isEqualTo("AUTH015");
        assertThat(AuthErrorCode.WITHDRAWN_ACCOUNT.getCode()).isEqualTo("AUTH016");
        assertThat(AuthErrorCode.EMAIL_WITHDRAWAL_PENDING.getCode()).isEqualTo("AUTH017");
    }
}
