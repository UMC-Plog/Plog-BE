package com.plog.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.plog.global.api.error.AuthErrorCode;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MediaCookieAuthenticationFilterTest {

    private final MediaTokenProvider mediaTokenProvider = mock(MediaTokenProvider.class);
    private final MediaCookieAuthenticationFilter filter =
            new MediaCookieAuthenticationFilter(mediaTokenProvider);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 쿠키가_유효하면_인증을_세팅한다() throws Exception {
        given(mediaTokenProvider.parseUserId("valid")).willReturn(7L);
        MockHttpServletRequest request = requestTo("/api/chat-attachments/3");
        request.setCookies(new Cookie(MediaCookieFactory.COOKIE_NAME, "valid"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(7L);
    }

    @Test
    void 다른_경로에서는_쿠키가_있어도_동작하지_않는다() throws Exception {
        MockHttpServletRequest request = requestTo("/api/chat-rooms/1/messages");
        request.setCookies(new Cookie(MediaCookieFactory.COOKIE_NAME, "valid"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 잘못된_토큰이면_인증_없이_에러코드만_남기고_체인을_계속한다() throws Exception {
        willThrow(new MalformedJwtException("bad"))
                .given(mediaTokenProvider).parseUserId(anyString());
        MockHttpServletRequest request = requestTo("/api/chat-attachments/3");
        request.setCookies(new Cookie(MediaCookieFactory.COOKIE_NAME, "broken"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    void 이미_헤더로_인증된_요청은_건드리지_않는다() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(99L, null, java.util.List.of()));
        MockHttpServletRequest request = requestTo("/api/chat-attachments/3");
        request.setCookies(new Cookie(MediaCookieFactory.COOKIE_NAME, "valid"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(99L);
    }

    @Test
    void 접두사만_같은_형제_경로는_잡지_않는다() throws Exception {
        MockHttpServletRequest request = requestTo("/api/chat-attachments-bulk/3");
        request.setCookies(new Cookie(MediaCookieFactory.COOKIE_NAME, "valid"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void 컨텍스트_패스가_붙어도_동작한다() throws Exception {
        given(mediaTokenProvider.parseUserId("valid")).willReturn(7L);
        MockHttpServletRequest request = requestTo("/plog/api/chat-attachments/3");
        request.setContextPath("/plog");
        request.setCookies(new Cookie(MediaCookieFactory.COOKIE_NAME, "valid"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(7L);
    }

    @Test
    void 쿠키가_없으면_아무것도_하지_않는다() throws Exception {
        MockHttpServletRequest request = requestTo("/api/chat-attachments/3");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestTo(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
