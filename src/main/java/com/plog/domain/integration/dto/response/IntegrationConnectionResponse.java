package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;

public record IntegrationConnectionResponse(
        Long projectId,
        LinkType linkType,
        String connectedAccountName
) {}
