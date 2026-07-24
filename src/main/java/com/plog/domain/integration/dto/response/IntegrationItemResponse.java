package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "provider별 외부 연동 상태")
public record IntegrationItemResponse(
        @Schema(description = "외부 provider 유형", example = "GITHUB",
                allowableValues = {"GITHUB", "FIGMA", "NOTION", "GOOGLE"})
        LinkType linkType,
        @Schema(description = "해당 provider가 프로젝트에 연결되어 있으면 true", example = "true")
        boolean linked,
        @Schema(description = "화면 표시용 연결 계정/워크스페이스 이름. 미연결이면 null", example = "UMC-Plog")
        String connectedAccountName
) {
}
