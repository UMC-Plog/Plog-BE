package com.plog.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 재발급/로그아웃 공통 — 클라이언트가 보관하던 리프레시 토큰 원문을 전달. */
public record TokenRefreshRequest(
        @NotBlank String refreshToken
) {
}
