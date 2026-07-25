package com.plog.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "이메일 인증 코드 검증 요청")
public record EmailVerifyRequest(
        @Schema(description = "인증할 이메일", example = "user@example.com")
        @NotBlank @Email String email,
        @Schema(description = "발송된 인증 코드", example = "123456")
        @NotBlank String code
) {
}
