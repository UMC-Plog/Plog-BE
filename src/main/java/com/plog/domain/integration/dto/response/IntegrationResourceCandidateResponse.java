package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.dto.NotionResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "provider에서 조회한 등록 후보 리소스")
public record IntegrationResourceCandidateResponse(
        @Schema(description = "provider 리소스 식별자", example = "1a2b3c4d5e6f")
        String providerResourceId,
        @Schema(description = "등록 요청에 그대로 넣을 Notion 대상 종류", example = "PAGE", allowableValues = {"PAGE", "DATA_SOURCE"})
        NotionResourceType resourceType,
        @Schema(description = "표시용 리소스 이름", example = "Plog 회의록")
        String resourceName,
        @Schema(description = "provider에서 열 수 있는 URL", example = "https://www.notion.so/...")
        String resourceUrl,
        @Schema(description = "provider 기준 마지막 수정 시각. provider가 제공하지 않으면 null", example = "2026-07-26T08:20:00Z")
        Instant lastModifiedAt
) {
}
