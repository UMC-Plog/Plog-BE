package com.plog.domain.project.dto.response;

import com.plog.domain.project.entity.ProjectType;
import java.time.LocalDate;

public record ProjectInvitationPreviewResponse(
        Long projectId,
        String projectName,
        ProjectType projectType,
        LocalDate endDay
) {
}
