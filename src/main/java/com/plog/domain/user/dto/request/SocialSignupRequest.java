package com.plog.domain.user.dto.request;

import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 소셜 가입 완료 요청. 이메일과 provider 정보는 티켓에서 꺼내므로 요청에 담지 않는다 —
 * 클라이언트가 보낸 이메일을 신뢰하면 임의의 주소로 계정을 만들 수 있다.
 */
@Schema(description = "소셜 가입 완료 요청")
public record SocialSignupRequest(
        @Schema(description = "소셜 인증 API가 발급한 가입 티켓", example = "3xL1n0...")
        @NotBlank String ticket,
        @Schema(description = "실명(가입 후 1회 변경 가능)", example = "홍길동")
        @NotBlank String name,
        @Schema(description = "닉네임", example = "조이")
        @NotBlank String nickname,
        @Schema(description = "프로필 프리셋(선택, null = 기본 아바타)", example = "OTTER", nullable = true)
        ProfilePreset profilePreset,
        @Schema(description = "약관 동의 목록(필수 3종 + 선택 마케팅)")
        @NotEmpty @Valid List<AgreementItem> agreements
) {
}
