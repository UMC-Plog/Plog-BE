package com.plog.global.security.jwt;

import com.plog.global.api.error.AuthErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * plog_media 쿠키를 인정하는 필터. <b>채팅 첨부 프록시 경로에서만</b> 동작한다.
 * <p>
 * CSRF 방어의 한 겹이다. 쿠키의 Path 스코프(1) + 이 경로 한정 매칭(2) + GET 전용(3)으로
 * 막는다. 다른 경로에서 쿠키를 인정하면 SecurityConfig 의 csrf().disable() 이 안전한
 * 전제(= 인증이 전부 헤더 기반)가 깨진다.
 * <p>
 * JwtAuthenticationFilter 와 같은 방식으로, 실패해도 응답을 끊지 않고 에러코드만 남기고
 * 체인을 계속한다. 최종 401 포맷은 JwtAuthenticationEntryPoint 가 맞춘다.
 */
@Component
public class MediaCookieAuthenticationFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/api/chat-attachments";

    private final MediaTokenProvider mediaTokenProvider;

    public MediaCookieAuthenticationFilter(MediaTokenProvider mediaTokenProvider) {
        this.mediaTokenProvider = mediaTokenProvider;
    }

    /**
     * 쿠키의 Path 스코프와 <b>정확히</b> 같은 범위만 매칭한다.
     * <p>
     * startsWith 만 쓰면 /api/chat-attachments-bulk 같은 형제 경로까지 걸린다(브라우저는
     * RFC 6265 path-match 규칙상 그런 경로에 쿠키를 안 보내므로 지금 당장 악용되지는
     * 않지만, 필터가 선언한 범위와 실제 범위가 어긋난 채로 두지 않는다).
     * <p>
     * getRequestURI() 는 컨텍스트 패스를 포함하므로 벗겨낸다. 안 벗기면
     * server.servlet.context-path 를 붙이는 순간 이 필터가 <b>조용히</b> 전혀 동작하지
     * 않아 모든 채팅 이미지가 401이 된다.
     * <p>
     * 경로 정규화(../ 등)는 Spring Security 의 기본 StrictHttpFirewall 이 400으로 막는다.
     * 커스텀 HttpFirewall 을 넣는다면 이 전제가 깨지므로 함께 검토해야 한다.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(PATH_PREFIX.equals(path) || path.startsWith(PATH_PREFIX + "/"));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<String> token = resolveCookieValue(request);
            if (token.isPresent()) {
                authenticate(request, token.get());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            Long userId = mediaTokenProvider.parseUserId(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException exception) {
            request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE,
                    AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException exception) {
            request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE,
                    AuthErrorCode.INVALID_TOKEN);
        }
    }

    private Optional<String> resolveCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> MediaCookieFactory.COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
