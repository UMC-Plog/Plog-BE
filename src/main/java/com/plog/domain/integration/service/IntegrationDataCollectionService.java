package com.plog.domain.integration.service;

import com.plog.domain.integration.dto.response.IntegrationCollectionFailureResponse;
import com.plog.domain.integration.dto.response.IntegrationCollectionResponse;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

/** 등록된 외부 연동 리소스의 provider 활동 원문을 수동으로 수집한다. */
@Service
public class IntegrationDataCollectionService {

    private static final int MAX_TEMPORARY_ATTEMPTS = 2;

    private final IntegrationResourceRepository integrationResourceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final IntegrationResourceService integrationResourceService;
    private final Map<LinkType, IntegrationResourceCollector> collectorByProvider;
    private final IntegrationActivityStoreService integrationActivityStoreService;
    private final IntegrationVerificationService integrationVerificationService;
    private final IntegrationResourceCollectionStateService resourceCollectionStateService;
    private final ConcurrentMap<Long, ReentrantLock> projectCollectionLocks = new ConcurrentHashMap<>();

    public IntegrationDataCollectionService(
            IntegrationResourceRepository integrationResourceRepository,
            ProjectRepository projectRepository,
            ProjectAccessService projectAccessService,
            ProjectIntegrationRepository projectIntegrationRepository,
            IntegrationResourceService integrationResourceService,
            List<IntegrationResourceCollector> collectors,
            IntegrationActivityStoreService integrationActivityStoreService,
            IntegrationVerificationService integrationVerificationService,
            IntegrationResourceCollectionStateService resourceCollectionStateService
    ) {
        this.integrationResourceRepository = integrationResourceRepository;
        this.projectRepository = projectRepository;
        this.projectAccessService = projectAccessService;
        this.projectIntegrationRepository = projectIntegrationRepository;
        this.integrationResourceService = integrationResourceService;
        this.collectorByProvider = collectorMap(collectors);
        this.integrationActivityStoreService = integrationActivityStoreService;
        this.integrationVerificationService = integrationVerificationService;
        this.resourceCollectionStateService = resourceCollectionStateService;
    }

    /** 진행 중 프로젝트도 수집할 수 있으며 프로젝트 상태는 변경하지 않는다. */
    public IntegrationCollectionResponse collectNow(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        projectAccessService.requireActiveMember(projectId, userId);
        ReentrantLock collectionLock = projectCollectionLocks.computeIfAbsent(
                projectId, ignored -> new ReentrantLock());
        collectionLock.lock();
        try {
            synchronizeGithubRepositories(projectId);
            CollectionOutcome outcome = collectResources(projectId);
            return new IntegrationCollectionResponse(
                    projectId,
                    outcome.requestedResourceCount(),
                    outcome.collectedResourceCount(),
                    outcome.failures().stream()
                            .map(failure -> new IntegrationCollectionFailureResponse(
                                    failure.resource().getId(),
                                    failure.resource().getProjectIntegration().getLinkType(),
                                    failure.resource().getResourceName(),
                                    failure.reason()))
                            .toList()
            );
        } finally {
            collectionLock.unlock();
        }
    }

    private CollectionOutcome collectResources(Long projectId) {
        List<CollectionFailure> failures = new ArrayList<>();
        Set<Long> verifiedIntegrationIds = new HashSet<>();
        List<IntegrationResource> resources = integrationResourceRepository
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        projectId, IntegrationResourceStatus.ACTIVE);

        int collectedResourceCount = 0;
        for (IntegrationResource resource : resources) {
            IntegrationResourceCollector collector =
                    collectorByProvider.get(resource.getProjectIntegration().getLinkType());
            if (collector == null) {
                failures.add(new CollectionFailure(resource, "collector unavailable"));
                continue;
            }
            if (collectResource(resource, collector, failures, verifiedIntegrationIds)) {
                collectedResourceCount++;
            }
        }
        return new CollectionOutcome(resources.size(), collectedResourceCount, List.copyOf(failures));
    }

    private Map<LinkType, IntegrationResourceCollector> collectorMap(
            List<IntegrationResourceCollector> collectors
    ) {
        Map<LinkType, IntegrationResourceCollector> collectorByProvider = new EnumMap<>(LinkType.class);
        for (IntegrationResourceCollector collector : collectors) {
            IntegrationResourceCollector duplicate = collectorByProvider.put(collector.provider(), collector);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate integration collector: " + collector.provider());
            }
        }
        return Map.copyOf(collectorByProvider);
    }

    private void synchronizeGithubRepositories(Long projectId) {
        projectIntegrationRepository.findByProjectIdAndLinkType(projectId, LinkType.GITHUB)
                .filter(ProjectIntegration::isConnected)
                .ifPresent(integrationResourceService::registerGithubInstallationRepositories);
    }

    private boolean collectResource(
            IntegrationResource resource,
            IntegrationResourceCollector collector,
            List<CollectionFailure> failures,
            Set<Long> verifiedIntegrationIds
    ) {
        for (int attempt = 1; attempt <= MAX_TEMPORARY_ATTEMPTS; attempt++) {
            try {
                integrationActivityStoreService.beginResourceCollection();
                verifyIntegrationIfNeeded(resource, verifiedIntegrationIds);
                collector.collect(resource);
                resourceCollectionStateService.markCollected(resource.getId(), Instant.now());
                return true;
            } catch (ProviderResourceAccessException exception) {
                if (handleProviderFailure(resource, failures, exception, attempt)) {
                    return false;
                }
            } catch (RuntimeException exception) {
                failures.add(new CollectionFailure(resource, "collection failed"));
                return false;
            } finally {
                integrationActivityStoreService.endResourceCollection();
            }
        }
        return false;
    }

    private void verifyIntegrationIfNeeded(IntegrationResource resource, Set<Long> verifiedIntegrationIds) {
        Long integrationId = resource.getProjectIntegration().getId();
        if (verifiedIntegrationIds.contains(integrationId)) {
            return;
        }
        integrationVerificationService.requireVerifiedConnection(
                resource.getProjectIntegration().getProject().getId(),
                resource.getProjectIntegration().getLinkType()
        );
        verifiedIntegrationIds.add(integrationId);
    }

    private boolean handleProviderFailure(
            IntegrationResource resource,
            List<CollectionFailure> failures,
            ProviderResourceAccessException exception,
            int attempt
    ) {
        if (exception.statusCode() == 401 || exception.statusCode() == 403) {
            resourceCollectionStateService.requireReauthorization(resource.getId());
            failures.add(new CollectionFailure(
                    resource,
                    exception.statusCode() == 401
                            ? "provider credential revoked"
                            : "provider resource access denied"
            ));
            return true;
        }
        if (exception.statusCode() == 404) {
            resourceCollectionStateService.disable(resource.getId());
            failures.add(new CollectionFailure(resource, "provider resource not found"));
            return true;
        }
        if (attempt == MAX_TEMPORARY_ATTEMPTS) {
            failures.add(new CollectionFailure(resource, "provider temporarily unavailable"));
            return true;
        }
        return false;
    }

    private record CollectionOutcome(
            int requestedResourceCount,
            int collectedResourceCount,
            List<CollectionFailure> failures
    ) {
    }

    private record CollectionFailure(IntegrationResource resource, String reason) {
    }
}
