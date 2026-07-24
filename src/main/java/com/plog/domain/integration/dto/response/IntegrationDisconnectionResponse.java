package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;

public record IntegrationDisconnectionResponse(
        Long projectId,
        LinkType linkType
) {}
