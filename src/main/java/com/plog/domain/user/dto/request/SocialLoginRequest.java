package com.plog.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 프론트가 provider 콜백에서 받은 인가 코드. state 대조는 프론트 책임이라 여기 담기지 않는다. */
@Schema(description = "소셜 인증 요청")
public record SocialLoginRequest(
        @Schema(description = "provider가 발급한 인가 코드", example = "0-a1B2c3...")
        @NotBlank String code
) {
}
