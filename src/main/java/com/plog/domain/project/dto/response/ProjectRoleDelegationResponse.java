package com.plog.domain.project.dto.response;

public record ProjectRoleDelegationResponse(
        Long projectId,
        Long newOwnerMemberId
) {
}
