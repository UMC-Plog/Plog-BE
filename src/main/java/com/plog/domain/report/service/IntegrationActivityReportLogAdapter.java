package com.plog.domain.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Locale;
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
    public void synchronizeProviderActorActivities(
            Long projectIntegrationId,
            String actorProviderId,
            String actorLogin,
            String actorEmail
    ) {
        String normalizedProviderId = blankToNull(actorProviderId);
        String normalizedLogin = normalizeAlias(actorLogin);
        String normalizedEmail = normalizeAlias(actorEmail);
        if (projectIntegrationId == null
                || (normalizedProviderId == null && normalizedLogin == null && normalizedEmail == null)) {
            return;
        }
        integrationActivityRepository.findReportProjectionTargetsByProviderActor(
                        projectIntegrationId,
                        normalizedProviderId,
                        normalizedLogin,
                        normalizedEmail
                )
                .forEach(this::synchronize);
    }

    @Transactional
    public void synchronizeProjectIntegrationActivities(Long projectIntegrationId) {
        if (projectIntegrationId == null) {
            return;
        }
        integrationActivityRepository.findReportProjectionTargetsByProjectIntegration(projectIntegrationId)
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
        sourceDomain(linkType).ifPresent(sourceDomain ->
                reportActivityLogRepository.deleteExternalActivityLogsBySourcePrefix(
                        sourceDomain.name(),
                        sourceRefPrefix(projectId, linkType)
                ));
    }

    @Transactional
    public void deleteResourceProjection(Long projectId, LinkType linkType, String providerResourceId) {
        if (projectId == null || linkType == null || providerResourceId == null || providerResourceId.isBlank()) {
            return;
        }
        sourceDomain(linkType).ifPresent(sourceDomain ->
                reportActivityLogRepository.deleteExternalActivityLogsBySourcePrefix(
                        sourceDomain.name(),
                        sourceRefPrefix(projectId, linkType) + sha256(providerResourceId) + ":"
                ));
    }

    private void synchronize(IntegrationActivity activity) {
        ProjectionSource source = toSource(activity.getIntegrationResource(), activity.getProviderEventKey())
                .orElse(null);
        if (source == null) {
            return;
        }
        ProjectionCommand command = toCommand(activity, source).orElse(null);
        if (command == null) {
            reportActivityLogRepository.deleteExternalActivityLog(source.sourceDomain().name(), source.sourceRefId());
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

    private Optional<ProjectionCommand> toCommand(IntegrationActivity activity, ProjectionSource source) {
        IntegrationResource resource = activity.getIntegrationResource();
        ProjectMember projectMember = activity.getProjectMember();
        IntegrationActivityType activityType = activity.getActivityType();
        Instant occurredAt = effectiveOccurredAt(activity);
        if (resource == null || resource.getProjectIntegration() == null
                || resource.getProjectIntegration().getProject() == null
                || projectMember == null || projectMember.getId() == null
                || activityType == null || occurredAt == null) {
            return Optional.empty();
        }
        if (activityType.requiresActorDisplay() && isBlank(activity.getActorLogin()) && isBlank(activity.getActorEmail())) {
            return Optional.empty();
        }
        if (isBot(source.sourceDomain(), activity.getActorLogin(), activity.getActorEmail())) {
            return Optional.empty();
        }
        RawActivityType rawActivityType = rawActivityType(activityType).orElse(null);
        if (rawActivityType == null || rawActivityType.owningDomain() != source.sourceDomain()) {
            return Optional.empty();
        }
        String metadata = safeJson(activity.getProviderPayload());
        if (competencyMapper.map(rawActivityType, metadata).isEmpty()
                && !isNotionEvidenceOnlySnapshot(rawActivityType)) {
            return Optional.empty();
        }

        Project project = resource.getProjectIntegration().getProject();
        LocalDate occurredDate = occurredAt.atZone(TimeUtil.STORAGE_ZONE).toLocalDate();
        if (isOutsideProjectPeriod(project, occurredDate)) {
            return Optional.empty();
        }

        return Optional.of(new ProjectionCommand(
                projectMember.getId(),
                source.sourceDomain(),
                rawActivityType,
                LocalDateTime.ofInstant(occurredAt, TimeUtil.STORAGE_ZONE),
                metadata,
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
        return switch (activityType) {
            case GITHUB_COMMIT -> Optional.of(RawActivityType.GITHUB_COMMIT);
            case GITHUB_PULL_REQUEST -> Optional.of(RawActivityType.GITHUB_PULL_REQUEST);
            case GITHUB_PULL_REQUEST_REVIEW -> Optional.of(RawActivityType.GITHUB_PULL_REQUEST_REVIEW);
            case GITHUB_ISSUE -> Optional.of(RawActivityType.GITHUB_ISSUE);
            case GITHUB_ISSUE_COMMENT -> Optional.of(RawActivityType.GITHUB_ISSUE_COMMENT);
            case GITHUB_ISSUE_EVENT -> Optional.of(RawActivityType.GITHUB_ISSUE_EVENT);
            case FIGMA_FILE_VERSION -> Optional.of(RawActivityType.FIGMA_FILE_VERSION);
            case FIGMA_FILE_METADATA -> Optional.of(RawActivityType.FIGMA_FILE_METADATA);
            case FIGMA_COMMENT -> Optional.of(RawActivityType.FIGMA_COMMENT);
            case FIGMA_COMMENT_REACTION -> Optional.of(RawActivityType.FIGMA_COMMENT_REACTION);
            case GOOGLE_DRIVE_FILE_SNAPSHOT -> Optional.of(RawActivityType.GOOGLE_DRIVE_FILE_SNAPSHOT);
            case GOOGLE_DRIVE_ACTIVITY -> Optional.of(RawActivityType.GOOGLE_DRIVE_ACTIVITY);
            case GOOGLE_DRIVE_COMMENT -> Optional.of(RawActivityType.GOOGLE_DRIVE_COMMENT);
            case GOOGLE_DRIVE_REVISION -> Optional.of(RawActivityType.GOOGLE_DRIVE_REVISION);
            case GOOGLE_DOCUMENT_SUGGESTION -> Optional.of(RawActivityType.GOOGLE_DOCUMENT_SUGGESTION);
            case GOOGLE_PRESENTATION_SNAPSHOT -> Optional.of(RawActivityType.GOOGLE_PRESENTATION_SNAPSHOT);
            case NOTION_DATA_SOURCE_SNAPSHOT -> Optional.of(RawActivityType.NOTION_DATA_SOURCE_SNAPSHOT);
            case NOTION_PAGE_SNAPSHOT -> Optional.of(RawActivityType.NOTION_PAGE_SNAPSHOT);
            case NOTION_BLOCK_SNAPSHOT -> Optional.of(RawActivityType.NOTION_BLOCK_SNAPSHOT);
            case NOTION_COMMENT -> Optional.of(RawActivityType.NOTION_COMMENT);
            case NOTION_WEBHOOK_EVENT -> Optional.empty();
        };
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

    private boolean isNotionEvidenceOnlySnapshot(RawActivityType rawActivityType) {
        return rawActivityType == RawActivityType.NOTION_PAGE_SNAPSHOT
                || rawActivityType == RawActivityType.NOTION_BLOCK_SNAPSHOT;
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

    private boolean isOutsideProjectPeriod(Project project, LocalDate occurredDate) {
        return (project.getStartDay() != null && occurredDate.isBefore(project.getStartDay()))
                || (project.getEndDay() != null && occurredDate.isAfter(project.getEndDay()));
    }

    private String safeJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return "{}";
        }
        try {
            JsonNode parsed = objectMapper.readTree(payload);
            return parsed != null && parsed.isObject() ? parsed.toString() : rawPayload(payload);
        } catch (JsonProcessingException exception) {
            return rawPayload(payload);
        }
    }

    private String rawPayload(String payload) {
        return objectMapper.createObjectNode().put("raw", payload).toString();
    }

    private Instant effectiveOccurredAt(IntegrationActivity activity) {
        if (activity.getOccurredAt() != null) {
            return activity.getOccurredAt();
        }
        LocalDateTime createdAt = activity.getCreatedAt();
        return createdAt == null ? null : createdAt.atZone(TimeUtil.STORAGE_ZONE).toInstant();
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
        return sourceDomain == SourceDomain.GITHUB && (isBotValue(actorLogin) || isBotValue(actorEmail));
    }

    private boolean isBotValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("[bot]")
                || normalized.equals("github-actions")
                || normalized.startsWith("github-actions@");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private String normalizeAlias(String value) {
        return isBlank(value) ? null : value.toLowerCase(Locale.ROOT);
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
}
