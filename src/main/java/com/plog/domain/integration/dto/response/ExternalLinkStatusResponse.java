package com.plog.domain.integration.dto.response;

import java.util.List;

public record ExternalLinkStatusResponse(
        Long projectId,
        Long projectMemberId,
        List<ExternalLinkItemResponse> links
) {
}
