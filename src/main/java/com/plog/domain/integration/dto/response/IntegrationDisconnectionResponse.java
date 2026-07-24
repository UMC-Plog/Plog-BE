package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "외부 provider 연동 해제 응답")
public record IntegrationDisconnectionResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "연동 해제된 provider 유형", example = "FIGMA",
                allowableValues = {"GITHUB", "FIGMA", "NOTION", "GOOGLE"})
        LinkType linkType
) {}
