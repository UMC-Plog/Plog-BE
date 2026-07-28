package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationIdentityAliasType;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentityAlias;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberIntegrationIdentityAliasRepository
        extends JpaRepository<ProjectMemberIntegrationIdentityAlias, Long> {

    List<ProjectMemberIntegrationIdentityAlias> findAllByProjectIntegrationIdAndAliasTypeAndAliasValue(
            Long projectIntegrationId,
            IntegrationIdentityAliasType aliasType,
            String aliasValue
    );
}
