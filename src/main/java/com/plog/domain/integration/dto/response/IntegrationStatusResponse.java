package com.plog.domain.integration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "프로젝트 외부 provider 연동 상태 응답")
public record IntegrationStatusResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "요청 사용자의 프로젝트 멤버 ID", example = "100")
        Long projectMemberId,
        @Schema(description = "provider별 연동 상태 목록. GITHUB, FIGMA, NOTION, GOOGLE 순서로 반환됩니다.")
        List<IntegrationItemResponse> integrations
) {
}
