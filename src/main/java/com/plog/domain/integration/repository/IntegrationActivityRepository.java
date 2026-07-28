package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationActivity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IntegrationActivityRepository extends JpaRepository<IntegrationActivity, Long> {

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into integration_activities "
            + "(integration_resource_id, project_member_id, activity_type, provider_event_key, "
            + "actor_provider_id, actor_login, actor_email, occurred_at, source_url, provider_payload, "
            + "created_at, updated_at) "
            + "values (:resourceId, :projectMemberId, :activityType, :providerEventKey, "
            + ":actorProviderId, :actorLogin, :actorEmail, :occurredAt, :sourceUrl, :providerPayload, "
            + "current_timestamp, current_timestamp) "
            + "on conflict (integration_resource_id, provider_event_key) do nothing", nativeQuery = true)
    int insertIfAbsent(
            @Param("resourceId") Long resourceId,
            @Param("projectMemberId") Long projectMemberId,
            @Param("activityType") String activityType,
            @Param("providerEventKey") String providerEventKey,
            @Param("actorProviderId") String actorProviderId,
            @Param("actorLogin") String actorLogin,
            @Param("actorEmail") String actorEmail,
            @Param("occurredAt") Instant occurredAt,
            @Param("sourceUrl") String sourceUrl,
            @Param("providerPayload") String providerPayload
    );

    void deleteAllByIntegrationResourceProjectIntegrationId(Long projectIntegrationId);
}
