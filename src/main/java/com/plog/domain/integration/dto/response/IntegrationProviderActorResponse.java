package com.plog.domain.integration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "수집 활동에서 발견된 provider 계정")
public record IntegrationProviderActorResponse(
        @Schema(description = "매핑 저장 요청에 그대로 사용하는 불투명 provider 계정 식별키",
                example = "actor:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        String actorKey,
        @Schema(description = "미매핑 후보에서는 원본 provider 사용자 ID를 노출하지 않으므로 항상 null",
                nullable = true)
        String providerActorId,
        @Schema(description = "provider 로그인명 또는 표시 이름. 이메일 형식이면 마스킹", example = "wantkdd")
        String providerLogin,
        @Schema(description = "provider가 활동에 제공한 마스킹 이메일. 제공하지 않으면 null",
                example = "v***@example.com")
        String providerEmail,
        @Schema(description = "화면 표시용 계정명. login·마스킹 이메일·불투명 키 일부 순서로 선택됩니다.",
                example = "wantkdd")
        String displayName,
        @Schema(description = "해당 provider 계정으로 수집된 활동 수", example = "24")
        long activityCount,
        @Schema(description = "처음 발견된 활동 시각. provider가 시각을 제공하지 않으면 null")
        Instant firstOccurredAt,
        @Schema(description = "마지막으로 발견된 활동 시각. provider가 시각을 제공하지 않으면 null")
        Instant lastOccurredAt
) {
}
