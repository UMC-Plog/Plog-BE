package com.plog.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 재발급/로그아웃 공통 — 클라이언트가 보관하던 리프레시 토큰 원문을 전달. */
@Schema(description = "토큰 재발급/로그아웃 요청")
public record TokenRefreshRequest(
        @Schema(description = "보관 중인 리프레시 토큰 원문", example = "eyJhbGciOiJIUzI1NiJ9...")
        @NotBlank String refreshToken
) {
}
