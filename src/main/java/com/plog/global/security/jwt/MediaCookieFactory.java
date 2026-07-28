package com.plog.global.security.jwt;

import com.plog.global.config.MediaProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * plog_media 쿠키 조립. 토큰을 발급하는 모든 응답이 이 컴포넌트 하나를 거치게 해서,
 * 한 경로만 빠져 "그 경로로 로그인한 사용자만 이미지가 안 뜨는" 버그를 막는다.
 */
@Component
public class MediaCookieFactory {

    public static final String COOKIE_NAME = "plog_media";
    public static final String COOKIE_PATH = "/api/chat-attachments";

    private final JwtProvider jwtProvider;
    private final MediaTokenProvider mediaTokenProvider;
    private final MediaProperties mediaProperties;

    public MediaCookieFactory(JwtProvider jwtProvider,
                              MediaTokenProvider mediaTokenProvider,
                              MediaProperties mediaProperties) {
        this.jwtProvider = jwtProvider;
        this.mediaTokenProvider = mediaTokenProvider;
        this.mediaProperties = mediaProperties;
    }

    /**
     * 방금 발급한 access token 에서 userId 를 꺼내 미디어 쿠키를 만든다.
     * <p>
     * 컨트롤러가 손에 쥔 것이 TokenResponse(accessToken/refreshToken)뿐이라 userId 를
     * 다시 얻어야 한다. 서비스 5곳의 반환 타입을 바꾸는 대신 방금 만든 토큰을 파싱한다.
     */
    public ResponseCookie issue(String accessToken) {
        Long userId = jwtProvider.parseUserId(accessToken);
        return base(mediaTokenProvider.createMediaToken(userId))
                .maxAge(mediaTokenProvider.ttl())
                .build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    // Domain 을 붙이지 않는다(host-only) — 이 쿠키를 받을 곳은 API 호스트 하나뿐이다.
    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(mediaProperties.cookieSameSite())
                .path(COOKIE_PATH);
    }
}
