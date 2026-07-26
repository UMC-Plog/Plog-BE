package com.plog.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "새 비밀번호 설정 요청")
public record PasswordResetRequest(
        @Schema(description = "인증을 완료한 이메일 주소", example = "hello@plog.com")
        @NotBlank @Email String email,
        @Schema(description = "새 비밀번호 (8자 이상, 영문과 숫자 포함)", example = "plog1234")
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."
        )
        String newPassword,
        // 형식 검사는 newPassword 에만 둔다. 확인 칸에 같은 @Pattern 을 붙이면
        // 오타로 형식까지 어긋났을 때 어느 칸이 문제인지 응답에서 구분되지 않는다.
        // 일치 여부는 서비스가 AUTH018 로 판정한다.
        @Schema(description = "새 비밀번호 확인 (위 값과 동일해야 함)", example = "plog1234")
        @NotBlank String newPasswordConfirm
) {
}
