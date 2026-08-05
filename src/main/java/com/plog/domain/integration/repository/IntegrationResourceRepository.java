package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface IntegrationResourceRepository extends JpaRepository<IntegrationResource, Long> {

    @EntityGraph(attributePaths = {"projectIntegration", "projectIntegration.project"})
    List<IntegrationResource> findAllByProjectIntegrationProjectIdAndResourceStatusOrderByIdAsc(
            Long projectId,
            IntegrationResourceStatus resourceStatus
    );

    @EntityGraph(attributePaths = {"projectIntegration", "projectIntegration.project"})
    List<IntegrationResource> findAllByProjectIntegrationProjectIdOrderByIdAsc(Long projectId);

    @EntityGraph(attributePaths = {"projectIntegration", "projectIntegration.project"})
    List<IntegrationResource> findAllByProjectIntegrationIdAndResourceStatusOrderByIdAsc(
            Long projectIntegrationId,
            IntegrationResourceStatus resourceStatus
    );

    List<IntegrationResource> findAllByProjectIntegrationIdOrderByIdAsc(Long projectIntegrationId);

    Optional<IntegrationResource> findByProjectIntegrationIdAndProviderResourceId(
            Long projectIntegrationId,
            String providerResourceId
    );

    Optional<IntegrationResource> findByIdAndProjectIntegrationId(Long id, Long projectIntegrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select resource from IntegrationResource resource "
            + "where resource.id = :resourceId and resource.projectIntegration.id = :projectIntegrationId")
    Optional<IntegrationResource> findByIdAndProjectIntegrationIdForUpdate(
            @Param("resourceId") Long id,
            @Param("projectIntegrationId") Long projectIntegrationId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IntegrationResource resource where resource.projectIntegration.id = :projectIntegrationId")
    void deleteAllByProjectIntegrationId(@Param("projectIntegrationId") Long projectIntegrationId);
}

