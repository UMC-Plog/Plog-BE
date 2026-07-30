package com.plog.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public final class FcmTokenDto {
    private FcmTokenDto() {}

    @Schema(name = "FcmTokenRequest")
    public record Request(
            @NotBlank(message = "유효하지 않은 토큰 형식입니다.")
            @Size(max = 512, message = "토큰 길이는 512자를 초과할 수 없습니다.")
            String token
    ) {
        public Request {
            token = token == null ? null : token.trim();
        }
    }

    @Schema(name = "FcmTokenResponse")
    public record Response(Long fcmId, Long userId, String token, Instant updatedAt) {}

    @Schema(name = "FcmTokenDeletedResponse")
    public record DeletedResponse(boolean deleted) {}
}
