package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
import com.plog.domain.report.service.IntegrationActivityReportLogAdapter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** provider event key 기준으로 활동 원문을 멱등 저장한다. */
@Service
@RequiredArgsConstructor
public class IntegrationActivityStoreService {

    private final IntegrationActivityRepository integrationActivityRepository;
    private final IntegrationActorMappingService integrationActorMappingService;
    private final IntegrationActivityReportLogAdapter reportLogAdapter;
    private final ThreadLocal<Map<ActorKey, Optional<com.plog.domain.project.entity.ProjectMember>>> actorCache
            = ThreadLocal.withInitial(HashMap::new);

    public void beginResourceCollection() {
        actorCache.get().clear();
    }

    public void endResourceCollection() {
        actorCache.remove();
    }

    @Transactional
    public void store(
            IntegrationResource resource,
            IntegrationActivityType activityType,
            String providerEventKey,
            String actorProviderId,
            String actorLogin,
            String actorEmail,
            Instant occurredAt,
            String sourceUrl,
            String providerPayload
    ) {
        if (providerEventKey == null || providerEventKey.isBlank()
                || lacksRequiredActorDisplay(activityType, actorLogin, actorEmail)) {
            return;
        }
        com.plog.domain.project.entity.ProjectMember projectMember =
                resolveActor(resource, actorProviderId, actorLogin, actorEmail);
        String payload = providerPayload == null ? "{}" : providerPayload;
        int inserted = integrationActivityRepository.insertIfAbsent(
                resource.getId(),
                projectMember == null ? null : projectMember.getId(),
                activityType.name(),
                providerEventKey,
                actorProviderId,
                actorLogin,
                actorEmail,
                occurredAt,
                sourceUrl,
                payload
        );
        if (inserted == 1 && occurredAt != null) {
            reportLogAdapter.upsert(
                    resource,
                    projectMember,
                    activityType,
                    providerEventKey,
                    actorLogin,
                    actorEmail,
                    occurredAt,
                    payload
            );
            return;
        }
        reportLogAdapter.synchronizeActivity(resource.getId(), providerEventKey);
    }

    /** 댓글 삭제처럼 같은 provider event key의 payload 상태가 바뀌는 항목에만 사용한다. */
    @Transactional
    public void storeLatestProviderPayload(
            IntegrationResource resource,
            IntegrationActivityType activityType,
            String providerEventKey,
            String actorProviderId,
            String actorLogin,
            String actorEmail,
            Instant occurredAt,
            String sourceUrl,
            String providerPayload
    ) {
        if (providerEventKey == null || providerEventKey.isBlank()) {
            return;
        }
        String payload = providerPayload == null ? "{}" : providerPayload;
        if (lacksRequiredActorDisplay(activityType, actorLogin, actorEmail)) {
            integrationActivityRepository.updateProviderPayloadIfChanged(
                    resource.getId(), providerEventKey, payload
            );
            reportLogAdapter.synchronizeActivity(resource.getId(), providerEventKey);
            return;
        }
        com.plog.domain.project.entity.ProjectMember projectMember =
                resolveActor(resource, actorProviderId, actorLogin, actorEmail);
        integrationActivityRepository.upsertProviderPayloadIfChanged(
                resource.getId(),
                projectMember == null ? null : projectMember.getId(),
                activityType.name(),
                providerEventKey,
                actorProviderId,
                actorLogin,
                actorEmail,
                occurredAt,
                sourceUrl,
                payload
        );
        reportLogAdapter.synchronizeActivity(resource.getId(), providerEventKey);
    }

    @Transactional
    public int backfillActorDisplayInfo(
            Long projectIntegrationId,
            String actorProviderId,
            String actorLogin,
            String actorEmail
    ) {
        if (projectIntegrationId == null || isBlank(actorProviderId)
                || (isBlank(actorLogin) && isBlank(actorEmail))) {
            return 0;
        }
        int updated = integrationActivityRepository.backfillActorSnapshotByProviderId(
                projectIntegrationId, actorProviderId, actorLogin, actorEmail
        );
        if (updated > 0) {
            reportLogAdapter.synchronizeProviderActorActivities(
                    projectIntegrationId, actorProviderId, null, null);
        }
        return updated;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** actor가 존재하는 활동은 이름 또는 이메일 중 하나라도 확인된 경우에만 저장한다. */
    private boolean lacksRequiredActorDisplay(
            IntegrationActivityType activityType,
            String actorLogin,
            String actorEmail
    ) {
        return activityType.requiresActorDisplay() && isBlank(actorLogin) && isBlank(actorEmail);
    }

    private com.plog.domain.project.entity.ProjectMember resolveActor(
            IntegrationResource resource,
            String actorProviderId,
            String actorLogin,
            String actorEmail
    ) {
        ActorKey key = new ActorKey(resource.getProjectIntegration().getId(), actorProviderId, actorLogin, actorEmail);
        return actorCache.get().computeIfAbsent(key, ignored -> Optional.ofNullable(
                integrationActorMappingService.resolve(resource.getProjectIntegration(), actorProviderId, actorLogin, actorEmail)
        )).orElse(null);
    }

    private record ActorKey(Long integrationId, String providerId, String login, String email) {
    }
}
