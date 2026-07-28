package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.IntegrationResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "프로젝트에 등록된 외부 연동 리소스")
public record IntegrationResourceResponse(
        @Schema(description = "Plog 리소스 ID", example = "1")
        Long resourceId,
        @Schema(description = "provider 리소스 식별자", example = "1a2b3c4d5e6f")
        String providerResourceId,
        @Schema(description = "리소스 종류: GITHUB_REPOSITORY, NOTION_PAGE, NOTION_DATA_SOURCE, GOOGLE_DOCUMENT, GOOGLE_PRESENTATION, FIGMA_FILE",
                example = "NOTION_PAGE",
                allowableValues = {
                        "GITHUB_REPOSITORY", "NOTION_PAGE", "NOTION_DATA_SOURCE",
                        "GOOGLE_DOCUMENT", "GOOGLE_PRESENTATION", "FIGMA_FILE"
                })
        IntegrationResourceType resourceType,
        @Schema(description = "표시용 리소스 이름", example = "Plog 회의록")
        String resourceName,
        @Schema(description = "원본 provider URL", example = "https://www.notion.so/...")
        String resourceUrl,
        @Schema(description = "리소스 접근 상태: ACTIVE, REAUTH_REQUIRED, DISABLED",
                example = "ACTIVE",
                allowableValues = {"ACTIVE", "REAUTH_REQUIRED", "DISABLED"})
        IntegrationResourceStatus resourceStatus,
        @Schema(description = "provider 기준 마지막 수정 시각. provider가 제공하지 않으면 null", example = "2026-07-26T08:20:00Z")
        Instant lastModifiedAt,
        @Schema(description = "마지막 수집 시각. 아직 수집 전이면 null", example = "2026-07-26T09:00:00Z")
        Instant lastCollectedAt
) {
}
