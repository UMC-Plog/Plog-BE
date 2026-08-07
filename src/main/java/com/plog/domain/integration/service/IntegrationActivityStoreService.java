package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.repository.IntegrationActivityRepository;
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
        if (providerEventKey == null || providerEventKey.isBlank()) {
            return;
        }
        com.plog.domain.project.entity.ProjectMember projectMember =
                resolveActor(resource, actorProviderId, actorLogin, actorEmail);
        integrationActivityRepository.insertIfAbsent(
                resource.getId(),
                projectMember == null ? null : projectMember.getId(),
                activityType.name(),
                providerEventKey,
                actorProviderId,
                actorLogin,
                actorEmail,
                occurredAt,
                sourceUrl,
                providerPayload == null ? "{}" : providerPayload
        );
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
                providerPayload == null ? "{}" : providerPayload
        );
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
        return integrationActivityRepository.backfillActorSnapshotByProviderId(
                projectIntegrationId, actorProviderId, actorLogin, actorEmail
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
