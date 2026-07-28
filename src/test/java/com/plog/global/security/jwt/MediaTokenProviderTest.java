package com.plog.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MediaTokenProviderTest {

    private static final String SECRET = "plog-test-secret-key-must-be-at-least-32-bytes";

    private final JwtProperties properties = new JwtProperties(
            SECRET, Duration.ofMinutes(30), Duration.ofDays(14), Duration.ofDays(14));
    private final MediaTokenProvider mediaTokenProvider = new MediaTokenProvider(properties);
    private final JwtProvider jwtProvider = new JwtProvider(properties);

    @Test
    void 자신이_발급한_토큰에서_userId를_꺼낸다() {
        String token = mediaTokenProvider.createMediaToken(7L);

        assertThat(mediaTokenProvider.parseUserId(token)).isEqualTo(7L);
    }

    @Test
    void access_토큰은_키가_달라_미디어_파서를_통과하지_못한다() {
        String accessToken = jwtProvider.createAccessToken(7L);

        assertThatThrownBy(() -> mediaTokenProvider.parseUserId(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 미디어_토큰은_다른_API_인증에_쓸_수_없다() {
        String mediaToken = mediaTokenProvider.createMediaToken(7L);

        assertThatThrownBy(() -> jwtProvider.parseUserId(mediaToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void ttl은_설정값을_그대로_돌려준다() {
        assertThat(mediaTokenProvider.ttl()).isEqualTo(Duration.ofDays(14));
    }
}
