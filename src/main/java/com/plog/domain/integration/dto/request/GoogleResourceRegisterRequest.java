package com.plog.domain.integration.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google Docs 또는 Slides 수집 대상 등록 요청")
public record GoogleResourceRegisterRequest(
        @NotBlank
        @Schema(description = "Google Picker가 반환한 선택 파일의 id. name, mimeType, url은 서버가 Drive API로 재조회합니다.",
                example = "1a2b3c4d5e6f")
        String fileId
) {
}
