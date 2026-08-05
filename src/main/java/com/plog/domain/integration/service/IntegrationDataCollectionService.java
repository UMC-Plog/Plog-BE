package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationCollectionJob;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.repository.IntegrationResourceRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import jakarta.persistence.PersistenceException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.web.client.RestClientResponseException;

/** 등록된 외부 연동 리소스의 provider 활동 원문을 수동으로 수집한다. */
@Service
@Slf4j
public class IntegrationDataCollectionService {

    private static final int MAX_TEMPORARY_ATTEMPTS = 2;
    private static final Duration BASE_RETRY_DELAY = Duration.ofMillis(200);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(5);

    private final IntegrationResourceRepository integrationResourceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final IntegrationResourceService integrationResourceService;
    private final Map<LinkType, IntegrationResourceCollector> collectorByProvider;
    private final IntegrationActivityStoreService integrationActivityStoreService;
    private final IntegrationVerificationService integrationVerificationService;
    private final IntegrationResourceCollectionStateService resourceCollectionStateService;
    private final ProjectIntegrationService projectIntegrationService;
    private final IntegrationCollectionJobService integrationCollectionJobService;

    public IntegrationDataCollectionService(
            IntegrationResourceRepository integrationResourceRepository,
            ProjectRepository projectRepository,
            ProjectAccessService projectAccessService,
            IntegrationResourceService integrationResourceService,
            List<IntegrationResourceCollector> collectors,
            IntegrationActivityStoreService integrationActivityStoreService,
            IntegrationVerificationService integrationVerificationService,
            IntegrationResourceCollectionStateService resourceCollectionStateService,
            ProjectIntegrationService projectIntegrationService,
            IntegrationCollectionJobService integrationCollectionJobService
    ) {
        this.integrationResourceRepository = integrationResourceRepository;
        this.projectRepository = projectRepository;
        this.projectAccessService = projectAccessService;
        this.integrationResourceService = integrationResourceService;
        this.collectorByProvider = collectorMap(collectors);
        this.integrationActivityStoreService = integrationActivityStoreService;
        this.integrationVerificationService = integrationVerificationService;
        this.resourceCollectionStateService = resourceCollectionStateService;
        this.projectIntegrationService = projectIntegrationService;
        this.integrationCollectionJobService = integrationCollectionJobService;
    }

    /**
     * 수집 요청을 큐에 넣기만 한다. 실제 수집은 {@code IntegrationCollectionJobWorker}가 수행한다.
     *
     * <p>수집 1건은 GitHub API를 수백 회 호출해 수 분이 걸린다. 요청 스레드에서 처리하면 ALB
     * 타임아웃에 끊기고, 끊긴 뒤에도 서버는 계속 호출해 rate limit만 태운다.</p>
     */
    public IntegrationCollectionJob enqueueCollection(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        ProjectMember member = projectAccessService.requireActiveMember(projectId, userId);
        return integrationCollectionJobService.enqueue(
                projectId, member == null ? null : member.getId());
    }

    /** 워커가 호출한다. 진행 중 프로젝트도 수집할 수 있으며 프로젝트 상태는 변경하지 않는다. */
    public CollectionOutcome runCollection(Long projectId, CollectionContext context) {
        try {
            integrationResourceService.registerGithubInstallationRepositories(projectId);
        } catch (ApiException exception) {
            // provider 일시 장애로 리포지토리 목록을 못 받은 것뿐이다. 잡을 죽이지 말고 재큐한다.
            if (exception.getErrorCode() == IntegrationErrorCode.PROVIDER_TEMPORARILY_UNAVAILABLE) {
                throw new CollectionRetryableException(
                        "github repository synchronization temporarily unavailable", null);
            }
            throw exception;
        }
        return collectResources(projectId, context);
    }

    private CollectionOutcome collectResources(Long projectId, CollectionContext context) {
        List<CollectionFailure> failures = new ArrayList<>();
        Set<Long> verifiedIntegrationIds = new HashSet<>();
        List<IntegrationResource> resources = integrationResourceRepository
                .findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
                        projectId, IntegrationResourceStatus.ACTIVE);

        int collectedResourceCount = 0;
        for (IntegrationResource resource : resources) {
            if (context.cursor().skipsResource(resource.getId())) {
                // 이전 attempt에서 완주한 리소스다. 성공 수에는 포함해야 집계가 맞는다.
                collectedResourceCount++;
                continue;
            }
            context.enterResource(resource.getId());
            IntegrationResourceCollector collector =
                    collectorByProvider.get(resource.getProjectIntegration().getLinkType());
            if (collector == null) {
                resourceCollectionStateService.markFailed(
                        resource.getId(), Instant.now(), "collector unavailable");
                failures.add(failure(resource, "collector unavailable"));
                continue;
            }
            resourceCollectionStateService.markPending(resource.getId(), Instant.now());
            if (collectResource(resource, collector, failures, verifiedIntegrationIds, context)) {
                collectedResourceCount++;
            }
        }
        return new CollectionOutcome(resources.size(), collectedResourceCount, List.copyOf(failures));
    }

    private CollectionFailure failure(IntegrationResource resource, String reason) {
        return new CollectionFailure(resource.getId(), resource.getResourceName(), reason);
    }

    private CollectionRetryableException rateLimited(ProviderResourceAccessException exception) {
        // x-ratelimit-reset이 없으면 Retry-After로 떨어진다. 둘 다 없을 때만 워커 백오프에 맡긴다.
        Duration delay = retryAfter(exception);
        return new CollectionRetryableException(
                "provider rate limit exceeded",
                delay == null ? null : Instant.now().plus(delay));
    }

    private Map<LinkType, IntegrationResourceCollector> collectorMap(List<IntegrationResourceCollector> collectors) {
        Map<LinkType, IntegrationResourceCollector> collectorByProvider = new EnumMap<>(LinkType.class);
        for (IntegrationResourceCollector collector : collectors) {
            for (LinkType linkType : collector.providers()) {
                IntegrationResourceCollector duplicate = collectorByProvider.put(linkType, collector);
                if (duplicate != null) {
                    throw new IllegalStateException("Duplicate integration collector: " + linkType);
                }
            }
        }
        return Map.copyOf(collectorByProvider);
    }

    private boolean collectResource(
            IntegrationResource resource,
            IntegrationResourceCollector collector,
            List<CollectionFailure> failures,
            Set<Long> verifiedIntegrationIds,
            CollectionContext context
    ) {
        for (int attempt = 1; attempt <= MAX_TEMPORARY_ATTEMPTS; attempt++) {
            try {
                resourceCollectionStateService.markRunning(resource.getId(), Instant.now());
                integrationActivityStoreService.beginResourceCollection();
                verifyIntegrationIfNeeded(resource, verifiedIntegrationIds);
                collector.collect(resource, context);
                resourceCollectionStateService.markCollected(resource.getId(), Instant.now());
                return true;
            } catch (ProviderResourceAccessException exception) {
                // rate limit 창은 보통 한 시간이라 5초 인라인 재시도로는 열리지 않는다.
                // 리소스를 실패시키지 말고 잡을 통째로 reset 이후로 미룬다.
                if (ProviderRateLimitSupport.isRateLimited(exception)) {
                    throw rateLimited(exception);
                }
                if (handleProviderFailure(resource, failures, exception, attempt)) {
                    return false;
                }
            } catch (CollectionRetryableException exception) {
                // 실패가 아니다. 워커가 커서를 저장하고 재큐한다.
                throw exception;
            } catch (DataAccessException | TransactionException | PersistenceException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.error("Integration resource collection failed. resourceId={}", resource.getId(), exception);
                String failure = "collection failed: " + exception.getClass().getSimpleName();
                resourceCollectionStateService.markFailed(resource.getId(), Instant.now(), failure);
                failures.add(failure(resource, failure));
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
        // GitHub는 rate limit 초과에도 403을 준다. 헤더로 가르지 않으면 멀쩡한 연동이
        // 재인증 필요 상태로 떨어져 사용자에게 불필요한 재연결을 강요하게 된다.
        if (exception.statusCode() == 401
                || (exception.statusCode() == 403 && !ProviderRateLimitSupport.isRateLimited(exception))) {
            projectIntegrationService.requireReauthorization(resource.getProjectIntegration().getId());
            failures.add(failure(
                    resource,
                    exception.statusCode() == 401
                            ? "provider credential revoked"
                            : "provider resource access denied"
            ));
            return true;
        }
        if (exception.statusCode() == 404) {
            resourceCollectionStateService.disable(resource.getId(), Instant.now());
            failures.add(failure(resource, "provider resource not found"));
            return true;
        }
        if (!isTemporaryFailure(exception)) {
            String reason = "provider request failed: HTTP " + exception.statusCode();
            resourceCollectionStateService.markFailed(resource.getId(), Instant.now(), reason);
            failures.add(failure(resource, reason));
            return true;
        }
        if (attempt == MAX_TEMPORARY_ATTEMPTS) {
            String reason = "provider temporarily unavailable";
            resourceCollectionStateService.markFailed(resource.getId(), Instant.now(), reason);
            failures.add(failure(resource, reason));
            return true;
        }
        resourceCollectionStateService.markRetrying(
                resource.getId(), Instant.now(), "provider temporarily unavailable");
        waitBeforeRetry(exception, attempt);
        return false;
    }

    private boolean isTemporaryFailure(ProviderResourceAccessException exception) {
        int statusCode = exception.statusCode();
        return statusCode == 429
                || statusCode >= 500
                || (statusCode == 403 && ProviderRateLimitSupport.isRateLimited(exception));
    }

    private void waitBeforeRetry(ProviderResourceAccessException exception, int attempt) {
        Duration delay = retryAfter(exception);
        if (delay == null) {
            delay = BASE_RETRY_DELAY.multipliedBy(1L << (attempt - 1));
        }
        delay = delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            // 대개 배포 종료 신호다. 실패로 확정하지 말고 잡을 재큐한다.
            throw new CollectionRetryableException("collection retry wait was interrupted", null);
        }
    }

    private Duration retryAfter(ProviderResourceAccessException exception) {
        // rate limit은 Retry-After 대신 x-ratelimit-reset으로 재개 시각을 알려준다.
        Duration resetDelay = ProviderRateLimitSupport.resetDelay(exception, Instant.now());
        if (resetDelay != null) {
            return resetDelay;
        }
        if (!(exception.getCause() instanceof RestClientResponseException responseException)
                || responseException.getResponseHeaders() == null) {
            return null;
        }
        String value = responseException.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0L, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            try {
                Duration delay = Duration.between(
                        Instant.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                );
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (RuntimeException invalidHeader) {
                return null;
            }
        }
    }

    public record CollectionOutcome(
            int requestedResourceCount,
            int collectedResourceCount,
            List<CollectionFailure> failures
    ) {
    }

    /**
     * IntegrationResource 대신 값만 들고 나간다. 워커는 트랜잭션 밖에서 이를 읽으므로
     * LAZY 프록시를 넘기면 LazyInitializationException이 난다.
     */
    public record CollectionFailure(Long resourceId, String resourceName, String reason) {
    }
}
