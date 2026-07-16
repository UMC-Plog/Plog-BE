package com.plog.domain.project.dto;

import com.plog.domain.project.entity.ProjectType;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectSettingsDto {
    private ProjectSettingsDto() {}

    public record UpdateRequest(
            @Size(min = 2, max = 20) String projectName,
            LocalDate endDay,
            ProjectType projectType
    ) {}

    public record Invite(String inviteUrl, String qrUrl) {}

    public record ExternalConnection(Long connectionId, String linkType, boolean isLinked) {}

    public record Response(
            Long projectId,
            String projectName,
            ProjectType projectType,
            String status,
            LocalDate startDay,
            LocalDate endDay,
            Invite invite,
            List<ExternalConnection> externalConnections,
            Instant updatedAt
    ) {}

    public record UpdateResponse(
            Long projectId,
            String projectName,
            ProjectType projectType,
            LocalDate endDay,
            Instant updatedAt
    ) {}
}
