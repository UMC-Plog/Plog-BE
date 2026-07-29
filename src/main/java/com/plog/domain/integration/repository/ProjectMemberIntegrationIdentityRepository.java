package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentity;
import com.plog.domain.project.entity.MemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberIntegrationIdentityRepository extends JpaRepository<ProjectMemberIntegrationIdentity, Long> {

    Optional<ProjectMemberIntegrationIdentity> findByProjectIntegrationIdAndProviderActorId(
            Long projectIntegrationId,
            String providerActorId
    );

    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    List<ProjectMemberIntegrationIdentity> findAllByProjectIntegrationId(Long projectIntegrationId);

    Optional<ProjectMemberIntegrationIdentity> findByProjectIntegrationIdAndProjectMemberId(
            Long projectIntegrationId,
            Long projectMemberId
    );

    @Query("select count(identity) from ProjectMemberIntegrationIdentity identity "
            + "where identity.projectIntegration.id = :projectIntegrationId "
            + "and identity.projectMember.status = :status")
    long countByProjectIntegrationIdAndProjectMemberStatus(
            @Param("projectIntegrationId") Long projectIntegrationId,
            @Param("status") MemberStatus status
    );
}
