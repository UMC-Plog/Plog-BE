package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "프로젝트 provider별 등록 외부 리소스 목록")
public record IntegrationResourceListResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "외부 provider: GITHUB, FIGMA, NOTION, GOOGLE",
                example = "NOTION",
                allowableValues = {"GITHUB", "FIGMA", "NOTION", "GOOGLE"})
        LinkType linkType,
        @Schema(description = "해당 provider에 등록된 수집 대상 리소스 목록")
        List<IntegrationResourceResponse> resources
) {
}
