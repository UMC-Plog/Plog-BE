package com.plog.domain.project.dto.request;

import com.plog.domain.project.entity.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record ProjectRoleDelegationRequest(
        @NotNull(message = "위임할 권한은 필수입니다.")
        ProjectRole role
) {
}
