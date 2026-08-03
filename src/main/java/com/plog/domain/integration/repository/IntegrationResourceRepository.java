package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationResource;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
