package com.plog.domain.user.dto.request;

import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 2단계 폼이지만 가입은 1 API. 1단계(실명/이메일/비밀번호) + 2단계(닉네임/프로필) + 약관 동의를 한 번에 수신.
 * 비밀번호 규칙은 서버에서도 검증(@Pattern) — 클라이언트 검증을 신뢰하지 않는다.
 * profilePreset은 선택 — null이면 기본(회색) 아바타. 커스텀 업로드는 없다.
 */
@Schema(description = "회원가입 요청")
public record SignupRequest(
        @Schema(description = "실명(가입 후 1회 변경 가능)", example = "홍길동")
        @NotBlank String name,
        @Schema(description = "인증 완료된 이메일", example = "user@example.com")
        @NotBlank @Email String email,
        @Schema(description = "비밀번호(8자 이상, 영문+숫자 포함)", example = "plog1234")
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."
        )
        String password,
        @Schema(description = "닉네임", example = "gildong")
        @NotBlank String nickname,
        @Schema(description = "프로필 프리셋(선택, null = 기본 아바타)", example = "OTTER", nullable = true)
        ProfilePreset profilePreset,
        @Schema(description = "약관 동의 목록(필수 3종 + 선택 마케팅)")
        @NotEmpty @Valid List<AgreementItem> agreements
) {
}
