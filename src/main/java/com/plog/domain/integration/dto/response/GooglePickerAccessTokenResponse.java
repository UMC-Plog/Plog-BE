package com.plog.domain.integration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "프로젝트 Google 연동 계정의 Picker용 단기 access token")
public record GooglePickerAccessTokenResponse(
        @Schema(description = "Google Picker에 전달할 OAuth access token")
        String accessToken,
        @Schema(description = "연결된 프로젝트 대표 Google 계정명", example = "team.plog@gmail.com")
        String connectedAccountName,
        @Schema(description = "access token 만료 시각", example = "2026-08-03T05:30:00Z")
        Instant expiresAt
) {
}
