package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationActivity;
import com.plog.domain.project.entity.ProjectMember;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /** Stable provider identity를 유지하면서 mutable payload만 최신 상태로 교체한다. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into integration_activities "
            + "(integration_resource_id, project_member_id, activity_type, provider_event_key, "
            + "actor_provider_id, actor_login, actor_email, occurred_at, source_url, provider_payload, "
            + "created_at, updated_at) "
            + "values (:resourceId, :projectMemberId, :activityType, :providerEventKey, "
            + ":actorProviderId, :actorLogin, :actorEmail, :occurredAt, :sourceUrl, :providerPayload, "
            + "current_timestamp, current_timestamp) "
            + "on conflict (integration_resource_id, provider_event_key) do update "
            + "set provider_payload = excluded.provider_payload, updated_at = current_timestamp "
            + "where integration_activities.provider_payload is distinct from excluded.provider_payload",
            nativeQuery = true)
    int upsertProviderPayloadIfChanged(
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

    /** 표시 가능한 actor가 사라진 mutable event는 새 행을 만들지 않고 기존 payload만 갱신한다. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update integration_activities "
            + "set provider_payload = :providerPayload, updated_at = current_timestamp "
            + "where integration_resource_id = :resourceId "
            + "and provider_event_key = :providerEventKey "
            + "and provider_payload is distinct from :providerPayload",
            nativeQuery = true)
    int updateProviderPayloadIfChanged(
            @Param("resourceId") Long resourceId,
            @Param("providerEventKey") String providerEventKey,
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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update IntegrationActivity activity "
            + "set activity.actorLogin = case "
            + "when (activity.actorLogin is null or trim(activity.actorLogin) = '') "
            + "and :actorLogin is not null and trim(:actorLogin) <> '' "
            + "then :actorLogin else activity.actorLogin end, "
            + "activity.actorEmail = case "
            + "when (activity.actorEmail is null or trim(activity.actorEmail) = '') "
            + "and :actorEmail is not null and trim(:actorEmail) <> '' "
            + "then :actorEmail else activity.actorEmail end "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and activity.actorProviderId = :actorProviderId "
            + "and (((activity.actorLogin is null or trim(activity.actorLogin) = '') "
            + "and :actorLogin is not null and trim(:actorLogin) <> '') "
            + "or ((activity.actorEmail is null or trim(activity.actorEmail) = '') "
            + "and :actorEmail is not null and trim(:actorEmail) <> ''))")
    int backfillActorSnapshotByProviderId(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("actorProviderId") String actorProviderId,
            @Param("actorLogin") String actorLogin,
            @Param("actorEmail") String actorEmail
    );

    @EntityGraph(attributePaths = {
            "integrationResource",
            "integrationResource.projectIntegration",
            "integrationResource.projectIntegration.project",
            "projectMember"
    })
    @Query("select activity from IntegrationActivity activity "
            + "where activity.integrationResource.id = :resourceId "
            + "and activity.providerEventKey = :providerEventKey")
    Optional<IntegrationActivity> findReportProjectionTarget(
            @Param("resourceId") Long resourceId,
            @Param("providerEventKey") String providerEventKey
    );

    @EntityGraph(attributePaths = {
            "integrationResource",
            "integrationResource.projectIntegration",
            "integrationResource.projectIntegration.project",
            "projectMember"
    })
    @Query("select activity from IntegrationActivity activity "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and activity.projectMember.id = :projectMemberId")
    List<IntegrationActivity> findReportProjectionTargetsByMember(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("projectMemberId") Long projectMemberId
    );

    @EntityGraph(attributePaths = {
            "integrationResource",
            "integrationResource.projectIntegration",
            "integrationResource.projectIntegration.project",
            "projectMember"
    })
    @Query("select activity from IntegrationActivity activity "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId "
            + "and ((:actorProviderId is not null and activity.actorProviderId = :actorProviderId) "
            + "or (activity.actorProviderId is null and :actorEmail is not null "
            + "and lower(activity.actorEmail) = :actorEmail) "
            + "or (activity.actorProviderId is null and :actorLogin is not null "
            + "and lower(activity.actorLogin) = :actorLogin))")
    List<IntegrationActivity> findReportProjectionTargetsByProviderActor(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("actorProviderId") String actorProviderId,
            @Param("actorLogin") String actorLogin,
            @Param("actorEmail") String actorEmail
    );

    @EntityGraph(attributePaths = {
            "integrationResource",
            "integrationResource.projectIntegration",
            "integrationResource.projectIntegration.project",
            "projectMember"
    })
    @Query("select activity from IntegrationActivity activity "
            + "where activity.integrationResource.projectIntegration.id = :projectIntegrationId")
    List<IntegrationActivity> findReportProjectionTargetsByProjectIntegration(
            @Param("projectIntegrationId") Long projectIntegrationId
    );

    void deleteAllByIntegrationResourceProjectIntegrationId(Long projectIntegrationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IntegrationActivity activity where activity.integrationResource.id = :resourceId")
    int deleteAllByIntegrationResourceId(@Param("resourceId") Long integrationResourceId);
}
