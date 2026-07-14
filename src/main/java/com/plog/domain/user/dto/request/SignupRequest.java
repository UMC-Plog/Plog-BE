package com.plog.domain.user.dto.request;

import com.plog.domain.user.entity.AgreementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 2단계 폼이지만 가입은 1 API. 1단계(실명/이메일/비밀번호) + 2단계(닉네임) + 약관 동의를 한 번에 수신.
 * 비밀번호 규칙은 서버에서도 검증(@Pattern) — 클라이언트 검증을 신뢰하지 않는다.
 */
public record SignupRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."
        )
        String password,
        @NotBlank String nickname,
        @NotEmpty @Valid List<AgreementItem> agreements
) {
    public record AgreementItem(
            @NotNull AgreementType agreementType,
            boolean agreed
    ) {
    }
}
