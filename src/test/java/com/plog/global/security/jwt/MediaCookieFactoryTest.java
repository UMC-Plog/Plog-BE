package com.plog.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.global.config.MediaProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class MediaCookieFactoryTest {

    private static final String SECRET = "plog-test-secret-key-must-be-at-least-32-bytes";

    private final JwtProperties jwtProperties = new JwtProperties(
            SECRET, Duration.ofMinutes(30), Duration.ofDays(14), Duration.ofDays(14),
            Duration.ofSeconds(60));
    private final JwtProvider jwtProvider = new JwtProvider(jwtProperties);
    private final MediaTokenProvider mediaTokenProvider = new MediaTokenProvider(jwtProperties);
    private final MediaProperties mediaProperties = new MediaProperties("None");
    private final MediaCookieFactory factory =
            new MediaCookieFactory(jwtProvider, mediaTokenProvider, mediaProperties);

    @Test
    void 쿠키에_설계대로의_속성이_붙는다() {
        ResponseCookie cookie = factory.issue(jwtProvider.createAccessToken(7L));

        assertThat(cookie.getName()).isEqualTo("plog_media");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
        assertThat(cookie.getPath()).isEqualTo("/api/chat-attachments");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void Domain을_붙이지_않는다() {
        ResponseCookie cookie = factory.issue(jwtProvider.createAccessToken(7L));

        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void 쿠키_값은_access_토큰의_userId로_만든_미디어_토큰이다() {
        ResponseCookie cookie = factory.issue(jwtProvider.createAccessToken(7L));

        assertThat(mediaTokenProvider.parseUserId(cookie.getValue())).isEqualTo(7L);
    }

    @Test
    void clear는_값이_비고_MaxAge가_0이다() {
        ResponseCookie cookie = factory.clear();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getPath()).isEqualTo("/api/chat-attachments");
    }

    @Test
    void SameSite는_설정값을_따른다() {
        MediaCookieFactory laxFactory = new MediaCookieFactory(
                jwtProvider, mediaTokenProvider, new MediaProperties("Lax"));

        assertThat(laxFactory.clear().getSameSite()).isEqualTo("Lax");
    }
}
