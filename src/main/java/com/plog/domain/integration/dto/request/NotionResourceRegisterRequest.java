package com.plog.domain.integration.dto.request;

import com.plog.domain.integration.dto.NotionResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Notion 수집 대상 등록 요청")
public record NotionResourceRegisterRequest(
        @NotNull
        @Schema(description = "선택한 Notion 대상 종류", example = "PAGE", allowableValues = {"PAGE", "DATA_SOURCE"})
        NotionResourceType resourceType,
        @NotBlank
        @Schema(description = "Notion 후보 조회 API가 반환한 page 또는 data source ID", example = "1a2b3c4d-5e6f-7890-abcd-ef1234567890")
        String providerResourceId
) {
}
