package com.plog.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class FcmTokenDto {
    private FcmTokenDto() {}

    public record Request(
            @NotBlank(message = "유효하지 않은 토큰 형식입니다.")
            @Size(max = 512, message = "토큰 길이는 512자를 초과할 수 없습니다.")
            String token
    ) {
        public Request {
            token = token == null ? null : token.trim();
        }
    }

    public record Response(Long fcmId, Long userId, String token, Instant updatedAt) {}

    public record DeletedResponse(boolean deleted) {}
}
