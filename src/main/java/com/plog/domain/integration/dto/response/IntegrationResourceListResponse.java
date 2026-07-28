package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "프로젝트 provider별 등록 외부 리소스 목록")
public record IntegrationResourceListResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "외부 provider", example = "NOTION")
        LinkType linkType,
        List<IntegrationResourceResponse> resources
) {
}
