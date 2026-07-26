package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberIntegrationIdentityRepository extends JpaRepository<ProjectMemberIntegrationIdentity, Long> {

    Optional<ProjectMemberIntegrationIdentity> findByProjectIntegrationIdAndProviderActorId(
            Long projectIntegrationId,
            String providerActorId
    );
}
