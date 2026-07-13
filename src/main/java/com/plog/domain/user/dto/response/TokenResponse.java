package com.plog.domain.user.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
