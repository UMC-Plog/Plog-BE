package com.plog.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/auth/** 요청의 도착 사실과 결과 상태를 남긴다.
 * <p>
 * 이게 없으면 "재발급 요청이 서버까지 왔는지"조차 확인할 수 없다. ApiException 은
 * GlobalExceptionHandler 가 로그 없이 응답만 만들고, 액세스 로그도 따로 없기 때문이다.
 * 실패 사유(어느 판정에서 걸렸는지)는 RefreshTokenService 의 refresh_* 로그와 짝을 이룬다.
 * <p>
 * 인증 경로에만 건다 — 전 구간에 걸면 로그량이 의미 없이 커진다.
 */
@Slf4j
public class AuthAccessLogFilter extends OncePerRequestFilter {

    // PWA(홈 화면 추가) 트래픽과 일반 브라우저를 구분하려면 클라이언트 정보가 필요하다. 길어서 잘라 남긴다.
    private static final int USER_AGENT_LIMIT = 80;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("auth_request {} {} status={} {}ms ua=\"{}\"",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    System.currentTimeMillis() - startedAt,
                    userAgent(request));
        }
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return "";
        }
        return userAgent.length() <= USER_AGENT_LIMIT
                ? userAgent
                : userAgent.substring(0, USER_AGENT_LIMIT);
    }
}
