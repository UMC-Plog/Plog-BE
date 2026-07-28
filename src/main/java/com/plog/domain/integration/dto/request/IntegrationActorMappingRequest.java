package com.plog.domain.integration.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "현재 프로젝트 멤버의 provider 계정 매핑 저장 요청")
public record IntegrationActorMappingRequest(
        @NotBlank
        @Schema(description = "provider 계정 목록 조회의 availableProviderActors에서 반환한 불투명 actorKey를 그대로 전달합니다.",
                example = "actor:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        String actorKey
) {
}
