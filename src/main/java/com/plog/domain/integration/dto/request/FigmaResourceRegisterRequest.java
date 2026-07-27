package com.plog.domain.integration.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Figma Design File 수집 대상 등록 요청")
public record FigmaResourceRegisterRequest(
        @NotBlank
        @Schema(description = "Figma Design File URL. 서버가 file key를 추출해 Figma API 접근 권한을 재검증합니다.",
                example = "https://www.figma.com/design/abc123/Plog")
        String fileUrl
) {
}
