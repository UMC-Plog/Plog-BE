package com.plog.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class FcmTokenDto {
    private FcmTokenDto() {}

    public record Request(@NotBlank @Size(max = 512) String token) {
        public Request {
            token = token == null ? null : token.trim();
        }
    }

    public record Response(Long fcmId, Long userId, String token, Instant updatedAt) {}

    public record DeletedResponse(boolean deleted) {}
}
