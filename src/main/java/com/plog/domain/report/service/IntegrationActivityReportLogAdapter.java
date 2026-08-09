package com.plog.domain.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.entity.IntegrationActivity;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.global.util.TimeUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrationActivityReportLogAdapter {

    private final IntegrationActivityRepository integrationActivityRepository;
    private final ReportActivityLogRepository reportActivityLogRepository;
    private final ExternalActivityCompetencyMapper competencyMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public void synchronizeActivity(Long resourceId, String providerEventKey) {
        if (resourceId == null || providerEventKey == null || providerEventKey.isBlank()) {
            return;
        }
        integrationActivityRepository.findReportProjectionTarget(resourceId, providerEventKey)
                .ifPresent(this::synchronize);
    }

    @Transactional
    public void synchronizeProjectMemberActivities(Long projectIntegrationId, Long projectMemberId) {
        if (projectIntegrationId == null || projectMemberId == null) {
            return;
        }
        integrationActivityRepository.findReportProjectionTargetsByMember(projectIntegrationId, projectMemberId)
                .forEach(this::synchronize);
    }

    @Transactional
    public void deleteProjectMemberProjection(Long projectId, LinkType linkType, Long projectMemberId) {
        if (projectId == null || linkType == null || projectMemberId == null) {
            return;
        }
        sourceDomain(linkType).ifPresent(sourceDomain ->
                reportActivityLogRepository.deleteExternalActivityLogsByMemberAndSourcePrefix(
                        projectMemberId,
                        sourceDomain.name(),
                        sourceRefPrefix(projectId, linkType)
                ));
    }

    @Transactional
    public void deleteProjectProjection(Long projectId, LinkType linkType) {
        if (projectId == null || linkType == null) {
            return;
        }
        deleteProjectionByPrefix(projectId, linkType, sourceRefPrefix(projectId, linkType));
    }

    @Transactional
    public void deleteResourceProjection(
            Long projectId,
            LinkType linkType,
            String providerResourceId
    ) {
        if (projectId == null || linkType == null
                || providerResourceId == null || providerResourceId.isBlank()) {
            return;
        }
        deleteProjectionByPrefix(
                projectId,
                linkType,
                sourceRefPrefix(projectId, linkType) + sha256(providerResourceId) + ":"
        );
    }

    @Transactional
    public void upsert(
            IntegrationResource resource,
            ProjectMember projectMember,
            IntegrationActivityType activityType,
            String providerEventKey,
            String actorLogin,
            String actorEmail,
            Instant occurredAt,
            String providerPayload
    ) {
        ProjectionSource source = toSource(resource, providerEventKey).orElse(null);
        if (source == null) {
            return;
        }

        ProjectionCommand command = toCommand(
                resource,
                projectMember,
                activityType,
                actorLogin,
                actorEmail,
                occurredAt,
                providerPayload,
                source
        ).orElse(null);
        if (command == null) {
            return;
        }
        reportActivityLogRepository.upsertExternalActivityLog(
                command.projectMemberId(),
                command.sourceDomain().name(),
                command.rawActivityType().name(),
                null,
                command.occurredAt(),
                command.metadata(),
                command.sourceRefId()
        );
    }

    private void synchronize(IntegrationActivity activity) {
        ProjectionSource source = toSource(activity.getIntegrationResource(), activity.getProviderEventKey())
                .orElse(null);
        if (source == null) {
            return;
        }
        ProjectionCommand command = toCommand(
                activity.getIntegrationResource(),
                activity.getProjectMember(),
                activity.getActivityType(),
                activity.getActorLogin(),
                activity.getActorEmail(),
                activity.getOccurredAt(),
                activity.getProviderPayload(),
                source
        ).orElse(null);
        if (command == null) {
            reportActivityLogRepository.deleteExternalActivityLog(
                    source.sourceDomain().name(),
                    source.sourceRefId()
            );
            return;
        }
        reportActivityLogRepository.upsertExternalActivityLog(
                command.projectMemberId(),
                command.sourceDomain().name(),
                command.rawActivityType().name(),
                null,
                command.occurredAt(),
                command.metadata(),
                command.sourceRefId()
        );
    }

    private Optional<ProjectionCommand> toCommand(
            IntegrationResource resource,
            ProjectMember projectMember,
            IntegrationActivityType activityType,
            String actorLogin,
            String actorEmail,
            Instant occurredAt,
            String providerPayload,
            ProjectionSource source
    ) {
        if (resource == null || resource.getProjectIntegration() == null
                || resource.getProjectIntegration().getProject() == null
                || activityType == null
                || occurredAt == null) {
            return Optional.empty();
        }
        if (activityType.requiresActorDisplay() && isBlank(actorLogin) && isBlank(actorEmail)) {
            return Optional.empty();
        }
        if (isBot(source.sourceDomain(), actorLogin, actorEmail)) {
            return Optional.empty();
        }
        if (competencyMapper.map(activityType, providerPayload).isEmpty()) {
            return Optional.empty();
        }

        RawActivityType rawActivityType = rawActivityType(activityType).orElse(null);
        if (rawActivityType == null) {
            return Optional.empty();
        }
        if (rawActivityType.owningDomain() != source.sourceDomain()) {
            return Optional.empty();
        }

        Project project = resource.getProjectIntegration().getProject();
        LocalDate occurredDate = occurredAt.atZone(TimeUtil.STORAGE_ZONE).toLocalDate();
        if (isOutsideProjectPeriod(project, occurredDate)) {
            return Optional.empty();
        }

        return Optional.of(new ProjectionCommand(
                projectMember == null ? null : projectMember.getId(),
                source.sourceDomain(),
                rawActivityType,
                LocalDateTime.ofInstant(occurredAt, TimeUtil.STORAGE_ZONE),
                safeJson(providerPayload),
                source.sourceRefId()
        ));
    }

    private Optional<ProjectionSource> toSource(IntegrationResource resource, String providerEventKey) {
        if (resource == null || resource.getProjectIntegration() == null
                || resource.getProjectIntegration().getProject() == null
                || providerEventKey == null || providerEventKey.isBlank()) {
            return Optional.empty();
        }
        SourceDomain sourceDomain = sourceDomain(resource.getProjectIntegration().getLinkType()).orElse(null);
        if (sourceDomain == null) {
            return Optional.empty();
        }
        return Optional.of(new ProjectionSource(
                sourceDomain,
                sourceRefId(
                        resource.getProjectIntegration().getProject().getId(),
                        resource.getProjectIntegration().getLinkType(),
                        resource.getProviderResourceId(),
                        providerEventKey
                )
        ));
    }

    private Optional<RawActivityType> rawActivityType(IntegrationActivityType activityType) {
        try {
            return Optional.of(RawActivityType.valueOf(activityType.name()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<SourceDomain> sourceDomain(LinkType linkType) {
        if (linkType == null) {
            return Optional.empty();
        }
        return Optional.of(switch (linkType) {
            case GITHUB -> SourceDomain.GITHUB;
            case FIGMA -> SourceDomain.FIGMA;
            case NOTION -> SourceDomain.NOTION;
            case GOOGLE_DOCS, GOOGLE_SLIDES -> SourceDomain.GOOGLE;
        });
    }

    private String sourceRefId(Long projectId, LinkType linkType, String providerResourceId, String providerEventKey) {
        return sourceRefPrefix(projectId, linkType)
                + "%s:%s".formatted(
                        sha256(providerResourceId == null ? "" : providerResourceId),
                        sha256(providerEventKey)
                );
    }

    private String sourceRefPrefix(Long projectId, LinkType linkType) {
        return "integration:%s:%s:".formatted(projectId, linkType.name());
    }

    private void deleteProjectionByPrefix(Long projectId, LinkType linkType, String sourceRefPrefix) {
        if (projectId == null || linkType == null) {
            return;
        }
        sourceDomain(linkType).ifPresent(sourceDomain ->
                reportActivityLogRepository.deleteExternalActivityLogsBySourcePrefix(
                        sourceDomain.name(), sourceRefPrefix));
    }

    private boolean isOutsideProjectPeriod(Project project, LocalDate occurredDate) {
        return (project.getStartDay() != null && occurredDate.isBefore(project.getStartDay()))
                || (project.getEndDay() != null && occurredDate.isAfter(project.getEndDay()));
    }

    private String safeJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(payload));
        } catch (JsonProcessingException exception) {
            try {
                return objectMapper.writeValueAsString(new RawPayload(payload));
            } catch (JsonProcessingException nestedException) {
                return "{}";
            }
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is required", exception);
        }
    }

    private boolean isBot(SourceDomain sourceDomain, String actorLogin, String actorEmail) {
        return sourceDomain == SourceDomain.GITHUB
                && (isBotValue(actorLogin) || isBotValue(actorEmail));
    }

    private boolean isBotValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.contains("[bot]")
                || normalized.equals("github-actions")
                || normalized.startsWith("github-actions@");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ProjectionCommand(
            Long projectMemberId,
            SourceDomain sourceDomain,
            RawActivityType rawActivityType,
            LocalDateTime occurredAt,
            String metadata,
            String sourceRefId
    ) {
    }

    private record ProjectionSource(SourceDomain sourceDomain, String sourceRefId) {
    }

    private record RawPayload(String raw) {
    }
}
