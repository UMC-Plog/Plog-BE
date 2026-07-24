package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "외부 provider 연동 완료 응답")
public record IntegrationConnectionResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "연동 완료된 provider 유형", example = "NOTION",
                allowableValues = {"GITHUB", "FIGMA", "NOTION", "GOOGLE"})
        LinkType linkType,
        @Schema(description = "화면 표시용 연결 계정/워크스페이스 이름", example = "플로그님의 워크스페이스")
        String connectedAccountName
) {}
