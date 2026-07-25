package com.plog.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 재설정 인증 코드 검증 요청")
public record PasswordResetVerifyRequest(
        @Schema(description = "가입한 이메일 주소", example = "hello@plog.com")
        @NotBlank @Email String email,
        @Schema(description = "발송된 6자리 인증 코드", example = "123456")
        @NotBlank String code
) {
}
