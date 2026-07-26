package com.plog.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "회원 탈퇴 요청")
public record WithdrawalRequest(
        @Schema(description = "탈퇴 안내를 확인하고 동의했는지 여부. true 가 아니면 거부됩니다(USER002)",
                example = "true")
        @NotNull Boolean agreed
) {
}
