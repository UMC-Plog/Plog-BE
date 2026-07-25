package com.plog.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 발급 응답")
public record TokenResponse(
        @Schema(description = "액세스 토큰 (요청 시 Authorization: Bearer 헤더로 전송)",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "리프레시 토큰 (재발급/로그아웃 시 전송)", example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken
) {
}
