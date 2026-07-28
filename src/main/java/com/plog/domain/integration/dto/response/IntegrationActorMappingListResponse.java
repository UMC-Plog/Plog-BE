package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "프로젝트 멤버별 provider 계정 매핑과 아직 선택되지 않은 provider 계정")
public record IntegrationActorMappingListResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "외부 provider", example = "GITHUB")
        LinkType linkType,
        @Schema(description = "현재 로그인 사용자의 프로젝트 멤버 ID", example = "3")
        Long currentProjectMemberId,
        @Schema(description = "프로젝트에서 저장된 팀원별 provider 계정 매핑")
        List<IntegrationActorMappingResponse> mappings,
        @Schema(description = "수집 활동에서 발견됐지만 아직 어느 멤버도 선택하지 않은 provider 계정")
        List<IntegrationProviderActorResponse> availableProviderActors
) {
}
