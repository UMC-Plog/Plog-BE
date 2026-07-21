package com.plog.domain.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.plog.domain.project.entity.ProjectStatus;

public final class ProjectStatusDto {
    private ProjectStatusDto() {
    }

    public record Request(
            ProjectStatus status
    ) {
    }

    public record Response(
            Long projectId,
            ProjectStatus currentStatus,
            @JsonProperty("isTimeoutApplied")
            boolean isTimeoutApplied,
            @JsonProperty("isPublished")
            boolean isPublished
    ) {
    }
}
