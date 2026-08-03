package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "외부 연동 수집 대상 제거 응답")
public record IntegrationResourceRemovalResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "provider 유형", example = "FIGMA",
                allowableValues = {"FIGMA", "NOTION", "GOOGLE"})
        LinkType linkType,
        @Schema(description = "제거된 리소스 ID", example = "12")
        Long resourceId
) {
}
