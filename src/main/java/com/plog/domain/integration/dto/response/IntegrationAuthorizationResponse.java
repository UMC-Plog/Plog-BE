package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;
import java.time.Instant;

public record IntegrationAuthorizationResponse(
        LinkType linkType,
        String authorizationUrl,
        Instant expiresAt
) {}
