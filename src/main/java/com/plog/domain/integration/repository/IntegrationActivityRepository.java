package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationActivity;
import com.plog.domain.project.entity.ProjectMember;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IntegrationActivityRepository extends JpaRepository<IntegrationActivity, Long> {

    @Transactional
    @Modifying(flushAutomatically = true)
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

    @Query("select activity.actorProviderId as actorProviderId, "
            + "lower(activity.actorLogin) as actorLogin, lower(activity.actorEmail) as actorEmail, "
            + "count(activity.id) as activityCount, "
            + "min(activity.occurredAt) as firstOccurredAt, max(activity.occurredAt) as lastOccurredAt "
            + "from IntegrationActivity activity "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and (activity.actorProviderId is not null or activity.actorLogin is not null "
            + "or activity.actorEmail is not null) "
            + "group by activity.actorProviderId, lower(activity.actorLogin), lower(activity.actorEmail)")
    List<IntegrationActorObservation> findActorObservations(
            @Param("projectIntegrationId") Long projectIntegrationId
    );

    @Query("select count(activity) > 0 "
            + "from IntegrationActivity activity "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and activity.projectMember is null "
            + "and ((activity.actorProviderId is not null and trim(activity.actorProviderId) <> '') "
            + "or (activity.actorLogin is not null and trim(activity.actorLogin) <> '') "
            + "or (activity.actorEmail is not null and trim(activity.actorEmail) <> ''))")
    boolean existsUnassignedActivityActorByProjectIntegrationId(
            @Param("projectIntegrationId") Long projectIntegrationId
    );

    @Modifying(flushAutomatically = true)
    @Query("update IntegrationActivity activity set activity.projectMember = :projectMember "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and activity.projectMember is null "
            + "and activity.actorProviderId = :actorProviderId")
    int assignProjectMemberByProviderId(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("projectMember") ProjectMember projectMember,
            @Param("actorProviderId") String actorProviderId
    );

    @Modifying(flushAutomatically = true)
    @Query("update IntegrationActivity activity set activity.projectMember = :projectMember "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and activity.projectMember is null "
            + "and activity.actorProviderId is null "
            + "and lower(activity.actorEmail) = :actorEmail")
    int assignProjectMemberByEmail(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("projectMember") ProjectMember projectMember,
            @Param("actorEmail") String actorEmail
    );

    @Modifying(flushAutomatically = true)
    @Query("update IntegrationActivity activity set activity.projectMember = :projectMember "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and activity.projectMember is null "
            + "and activity.actorProviderId is null "
            + "and lower(activity.actorLogin) = :actorLogin")
    int assignProjectMemberByLogin(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("projectMember") ProjectMember projectMember,
            @Param("actorLogin") String actorLogin
    );

    @Modifying(flushAutomatically = true)
    @Query("update IntegrationActivity activity set activity.projectMember = null "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and activity.projectMember = :expectedMember "
            + "and activity.actorProviderId = :actorProviderId")
    int clearProjectMemberByProviderId(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("expectedMember") ProjectMember expectedMember,
            @Param("actorProviderId") String actorProviderId
    );

    @Modifying(flushAutomatically = true)
    @Query("update IntegrationActivity activity set activity.projectMember = null "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and activity.projectMember = :expectedMember "
            + "and activity.actorProviderId is null "
            + "and lower(activity.actorEmail) = :actorEmail")
    int clearProjectMemberByEmail(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("expectedMember") ProjectMember expectedMember,
            @Param("actorEmail") String actorEmail
    );

    @Modifying(flushAutomatically = true)
    @Query("update IntegrationActivity activity set activity.projectMember = null "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and activity.projectMember = :expectedMember "
            + "and activity.actorProviderId is null "
            + "and lower(activity.actorLogin) = :actorLogin")
    int clearProjectMemberByLogin(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("expectedMember") ProjectMember expectedMember,
            @Param("actorLogin") String actorLogin
    );

    void deleteAllByIntegrationResourceProjectIntegrationId(Long projectIntegrationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IntegrationActivity activity where activity.integrationResource.id = :resourceId")
    int deleteAllByIntegrationResourceId(@Param("resourceId") Long integrationResourceId);
}
