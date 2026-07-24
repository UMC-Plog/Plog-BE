package com.plog.domain.integration.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.plog.domain.integration.entity.LinkType;
import java.time.Instant;

public record IntegrationAuthorizationResponse(
        LinkType linkType,
        @JsonProperty("authorization")
        String authorizationUrl,
        Instant expiresAt
) {}
