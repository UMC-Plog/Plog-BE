package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.NotionWebhookEvent;
import com.plog.domain.integration.entity.NotionWebhookEventStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotionWebhookEventRepository extends JpaRepository<NotionWebhookEvent, Long> {

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into notion_webhook_events "
            + "(event_id, subscription_id, workspace_id, notion_integration_id, event_type, "
            + "entity_id, entity_type, parent_id, parent_type, authors_json, occurred_at, raw_payload, "
            + "status, available_at, attempt_count, created_at, updated_at, version) "
            + "values (:eventId, :subscriptionId, :workspaceId, :notionIntegrationId, :eventType, "
            + ":entityId, :entityType, :parentId, :parentType, :authorsJson, :occurredAt, :rawPayload, "
            + "'PENDING', :availableAt, 0, current_timestamp, current_timestamp, 0) "
            + "on conflict (event_id) do nothing", nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") String eventId,
            @Param("subscriptionId") String subscriptionId,
            @Param("workspaceId") String workspaceId,
            @Param("notionIntegrationId") String notionIntegrationId,
            @Param("eventType") String eventType,
            @Param("entityId") String entityId,
            @Param("entityType") String entityType,
            @Param("parentId") String parentId,
            @Param("parentType") String parentType,
            @Param("authorsJson") String authorsJson,
            @Param("occurredAt") Instant occurredAt,
            @Param("rawPayload") String rawPayload,
            @Param("availableAt") Instant availableAt
    );

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotionWebhookEvent event set event.availableAt = "
            + "case when event.availableAt > :availableAt then event.availableAt else :availableAt end "
            + "where event.workspaceId = :workspaceId and event.entityId = :entityId "
            + "and event.status in :statuses")
    int postponePendingGroup(
            @Param("workspaceId") String workspaceId,
            @Param("entityId") String entityId,
            @Param("statuses") Collection<NotionWebhookEventStatus> statuses,
            @Param("availableAt") Instant availableAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotionWebhookEvent event "
            + "where event.status in :statuses and event.availableAt <= :now "
            + "order by event.availableAt asc, event.id asc")
    List<NotionWebhookEvent> findDueForUpdate(
            @Param("statuses") Collection<NotionWebhookEventStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotionWebhookEvent event "
            + "where event.workspaceId = :workspaceId and event.entityId = :entityId "
            + "and event.status in :statuses and event.availableAt <= :now "
            + "order by event.occurredAt asc, event.id asc")
    List<NotionWebhookEvent> findGroupForUpdate(
            @Param("workspaceId") String workspaceId,
            @Param("entityId") String entityId,
            @Param("statuses") Collection<NotionWebhookEventStatus> statuses,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotionWebhookEvent event where event.id in :ids order by event.id asc")
    List<NotionWebhookEvent> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotionWebhookEvent event "
            + "where event.status = :status and event.startedAt < :staleBefore order by event.id asc")
    List<NotionWebhookEvent> findStaleForUpdate(
            @Param("status") NotionWebhookEventStatus status,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable
    );

    @Transactional
    @Modifying
    @Query("delete from NotionWebhookEvent event where event.status in :statuses "
            + "and event.finishedAt < :finishedBefore")
    int deleteTerminalEventsBefore(
            @Param("statuses") Collection<NotionWebhookEventStatus> statuses,
            @Param("finishedBefore") Instant finishedBefore
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from NotionWebhookEvent event where event.notionIntegrationId = :notionIntegrationId")
    int deleteAllByNotionIntegrationId(@Param("notionIntegrationId") String notionIntegrationId);

}
