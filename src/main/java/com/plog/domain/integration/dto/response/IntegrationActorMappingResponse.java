package com.plog.domain.integration.dto.response;

import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트 멤버와 provider 계정의 명시적 매핑")
public record IntegrationActorMappingResponse(
        @Schema(description = "Plog actor 매핑 ID", example = "10")
        Long mappingId,
        @Schema(description = "프로젝트 멤버 ID", example = "3")
        Long projectMemberId,
        @Schema(description = "프로젝트 멤버 실명", example = "유상완")
        String memberName,
        @Schema(description = "프로젝트 멤버 닉네임", example = "바나")
        String memberNickname,
        @Schema(description = "프로젝트 멤버 프로필 프리셋. 선택하지 않았으면 null", example = "OTTER")
        ProfilePreset profilePreset,
        @Schema(description = "현재 매핑된 provider 계정의 불투명 식별키",
                example = "actor:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        String actorKey,
        @Schema(description = "provider actor ID. 본인 매핑에서만 제공하며 다른 멤버 매핑에서는 null", example = "12345678")
        String providerActorId,
        @Schema(description = "provider 로그인명 또는 표시명. 이메일 형식이면 마스킹", example = "wantkdd")
        String providerLogin,
        @Schema(description = "마스킹된 provider actor 이메일", example = "v***@example.com")
        String providerEmail
) {
}
