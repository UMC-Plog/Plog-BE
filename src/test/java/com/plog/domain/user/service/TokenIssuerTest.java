package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TokenIssuerTest {

    @Test
    @DisplayName("액세스 토큰과 리프레시 토큰을 함께 발급한다")
    void issuesBothTokens() {
        JwtProvider jwtProvider = mock(JwtProvider.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        User user = User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
        ReflectionTestUtils.setField(user, "id", 42L);
        given(jwtProvider.createAccessToken(42L)).willReturn("access");
        given(refreshTokenService.issue(user)).willReturn("refresh");

        TokenResponse tokens = new TokenIssuer(jwtProvider, refreshTokenService).issue(user);

        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(tokens.refreshToken()).isEqualTo("refresh");
    }
}
