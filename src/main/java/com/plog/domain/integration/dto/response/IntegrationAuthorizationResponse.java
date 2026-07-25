package com.plog.domain.integration.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "외부 provider 승인 URL 발급 응답")
public record IntegrationAuthorizationResponse(
        @Schema(description = "승인을 시작할 provider 유형", example = "GITHUB",
                allowableValues = {"GITHUB", "FIGMA", "NOTION", "GOOGLE"})
        LinkType linkType,
        @JsonProperty("authorization")
        @Schema(description = "provider 승인 화면으로 이동할 URL", example = "https://github.com/apps/umc-plog/installations/new?state=...")
        String authorizationUrl,
        @Schema(description = "발급된 state 만료 시각", example = "2026-07-24T13:30:00Z")
        Instant expiresAt
) {}
