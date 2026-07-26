package com.plog.domain.user.dto.request;

import com.plog.domain.user.entity.AgreementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 약관 동의 항목. 이메일 가입과 소셜 가입이 같은 형태로 전송한다. */
@Schema(description = "약관 동의 항목")
public record AgreementItem(
        @Schema(description = "약관 종류", example = "SERVICE_TERMS")
        @NotNull AgreementType agreementType,
        @Schema(description = "동의 여부", example = "true")
        boolean agreed
) {
}
