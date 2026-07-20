package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;

public record ExternalLinkItemResponse(
        LinkType linkType,
        boolean linked,
        String connectedAccountName
) {
}
