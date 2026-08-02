package com.plog.domain.integration.service;

import com.plog.domain.integration.dto.response.IntegrationItemResponse;
import com.plog.domain.integration.dto.response.IntegrationDisconnectionResponse;
import com.plog.domain.integration.dto.response.IntegrationStatusResponse;
import com.plog.domain.integration.entity.IntegrationCollectionRun;
import com.plog.domain.integration.entity.IntegrationCollectionStatus;
import com.plog.domain.integration.entity.IntegrationConnectionStatus;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationCollectionRunRepository;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrationService {

    private static final List<LinkType> SUPPORTED_LINK_TYPES = List.of(
            LinkType.GITHUB,
            LinkType.FIGMA,
            LinkType.NOTION,
            LinkType.GOOGLE
    );

    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationResourceRepository integrationResourceRepository;
    private final IntegrationCollectionRunRepository integrationCollectionRunRepository;
    private final ProjectIntegrationService projectIntegrationService;

    public IntegrationStatusResponse getProjectIntegrations(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        ProjectMember projectMember = projectAccessService.requireActiveMember(projectId, userId);

        Map<LinkType, ProjectIntegration> connectionsByType = projectIntegrationRepository
                .findAllByProjectIdOrderByLinkTypeAsc(projectId)
                .stream()
                .collect(Collectors.toMap(
                        ProjectIntegration::getLinkType,
                        Function.identity(),
                        (existing, ignored) -> existing
                ));

        Map<Long, List<IntegrationResource>> resourcesByIntegrationId = integrationResourceRepository
                .findAllByProjectIntegrationProjectIdOrderByIdAsc(projectId)
                .stream()
                .collect(Collectors.groupingBy(
                        resource -> resource.getProjectIntegration().getId(),
                        Collectors.toCollection(ArrayList::new)
                ));

        List<IntegrationItemResponse> integrations = SUPPORTED_LINK_TYPES.stream()
                .map(linkType -> {
                    ProjectIntegration integration = connectionsByType.get(linkType);
                    List<IntegrationResource> resources = integration == null
                            ? List.of()
                            : resourcesByIntegrationId.getOrDefault(integration.getId(), List.of());
                    return toResponse(linkType, integration, resources);
                })
                .toList();

        IntegrationCollectionRun finalRun = integrationCollectionRunRepository.findByProjectId(projectId)
                .orElse(null);
        return new IntegrationStatusResponse(
                projectId,
                projectMember.getId(),
                integrations,
                finalRun == null ? null : finalRun.getStatus(),
                finalRun == null ? null : finalRun.getFailureSummary()
        );
    }

    @Transactional
    public IntegrationDisconnectionResponse disconnect(Long projectId, Long userId, LinkType linkType) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));
        projectAccessService.requireActiveMember(projectId, userId);
        if (project.isCompleted()) {
            throw new ApiException(IntegrationErrorCode.WORKSPACE_INTEGRATION_LOCKED);
        }

        projectIntegrationService.disconnect(projectId, linkType);
        return new IntegrationDisconnectionResponse(projectId, linkType);
    }

    private IntegrationItemResponse toResponse(
            LinkType linkType,
            ProjectIntegration integration,
            List<IntegrationResource> resources
    ) {
        if (integration == null) {
            return new IntegrationItemResponse(
                    linkType, false, null, null, false,
                    IntegrationCollectionStatus.NOT_STARTED, null, null);
        }

        IntegrationConnectionStatus connectionStatus = integration.getConnectionStatus();
        boolean reauthorizationRequired = connectionStatus == IntegrationConnectionStatus.REAUTH_REQUIRED;
        ResourceCollectionSummary collection = summarizeCollection(resources, reauthorizationRequired);
        return new IntegrationItemResponse(
                linkType,
                integration.isConnected(),
                connectionStatus == IntegrationConnectionStatus.REVOKED
                        ? null
                        : integration.getExternalAccountName(),
                connectionStatus,
                reauthorizationRequired,
                collection.status(),
                collection.lastCollectedAt(),
                collection.failure()
        );
    }

    private ResourceCollectionSummary summarizeCollection(
            List<IntegrationResource> resources,
            boolean integrationReauthorizationRequired
    ) {
        if (integrationReauthorizationRequired) {
            return new ResourceCollectionSummary(
                    IntegrationCollectionStatus.REAUTH_REQUIRED,
                    latestCollectedAt(resources),
                    latestFailure(resources, "provider reauthorization required")
            );
        }
        if (resources.isEmpty()) {
            return new ResourceCollectionSummary(IntegrationCollectionStatus.NOT_STARTED, null, null);
        }

        boolean succeeded = resources.stream()
                .anyMatch(resource -> resource.getCollectionStatus() == IntegrationCollectionStatus.SUCCEEDED);
        boolean failed = resources.stream()
                .anyMatch(resource -> resource.getCollectionStatus() == IntegrationCollectionStatus.FAILED);
        IntegrationCollectionStatus status;
        if (resources.stream().anyMatch(resource ->
                resource.getCollectionStatus() == IntegrationCollectionStatus.REAUTH_REQUIRED)) {
            status = IntegrationCollectionStatus.REAUTH_REQUIRED;
        } else if (resources.stream().anyMatch(resource ->
                resource.getCollectionStatus() == IntegrationCollectionStatus.RUNNING)) {
            status = IntegrationCollectionStatus.RUNNING;
        } else if (resources.stream().anyMatch(resource ->
                resource.getCollectionStatus() == IntegrationCollectionStatus.RETRYING)) {
            status = IntegrationCollectionStatus.RETRYING;
        } else if (succeeded && failed) {
            status = IntegrationCollectionStatus.PARTIAL_FAILED;
        } else if (failed) {
            status = IntegrationCollectionStatus.FAILED;
        } else if (resources.stream().anyMatch(resource ->
                resource.getCollectionStatus() == IntegrationCollectionStatus.PENDING)) {
            status = IntegrationCollectionStatus.PENDING;
        } else if (resources.stream().allMatch(resource ->
                resource.getCollectionStatus() == IntegrationCollectionStatus.SUCCEEDED)) {
            status = IntegrationCollectionStatus.SUCCEEDED;
        } else {
            status = IntegrationCollectionStatus.NOT_STARTED;
        }
        return new ResourceCollectionSummary(
                status,
                latestCollectedAt(resources),
                latestFailure(resources, null)
        );
    }

    private Instant latestCollectedAt(List<IntegrationResource> resources) {
        return resources.stream()
                .map(IntegrationResource::getLastCollectedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String latestFailure(List<IntegrationResource> resources, String fallback) {
        return resources.stream()
                .filter(resource -> resource.getLastCollectionFailure() != null)
                .max(Comparator.comparing(
                        IntegrationResource::getCollectionStatusUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .map(IntegrationResource::getLastCollectionFailure)
                .orElse(fallback);
    }

    private record ResourceCollectionSummary(
            IntegrationCollectionStatus status,
            Instant lastCollectedAt,
            String failure
    ) {
    }
}
