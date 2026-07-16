package com.plog.global.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.global.api.code.BaseErrorCode;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 미인증 요청(401) 처리. 필터에서 토큰 검증에 실패하면 request attribute에 담아둔 구체 에러코드
 * (만료/무효)를 꺼내 응답 포맷을 GlobalExceptionHandler와 동일하게 맞춘다. 없으면 UNAUTHORIZED.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        BaseErrorCode errorCode = (attribute instanceof AuthErrorCode code) ? code : ErrorCode.UNAUTHORIZED;
        writeError(response, errorCode);
    }

    private void writeError(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(errorCode));
    }
}
