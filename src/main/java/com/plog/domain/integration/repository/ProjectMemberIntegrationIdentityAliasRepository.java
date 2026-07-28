package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationIdentityAliasType;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentityAlias;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberIntegrationIdentityAliasRepository
        extends JpaRepository<ProjectMemberIntegrationIdentityAlias, Long> {

    List<ProjectMemberIntegrationIdentityAlias> findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(
            Long projectIntegrationId,
            IntegrationIdentityAliasType aliasType,
            String aliasValue
    );

    @EntityGraph(attributePaths = {"identity", "identity.projectMember", "identity.projectMember.user"})
    List<ProjectMemberIntegrationIdentityAlias> findAllByProjectIntegrationId(Long projectIntegrationId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ProjectMemberIntegrationIdentityAlias alias where alias.identity.id = :identityId")
    void deleteAllByIdentityId(@Param("identityId") Long identityId);
}
