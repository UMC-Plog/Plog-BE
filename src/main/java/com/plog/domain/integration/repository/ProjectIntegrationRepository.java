package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectIntegrationRepository extends JpaRepository<ProjectIntegration, Long> {

    List<ProjectIntegration> findAllByProjectIdOrderByLinkTypeAsc(Long projectId);

    Optional<ProjectIntegration> findByProjectIdAndLinkType(Long projectId, LinkType linkType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select integration from ProjectIntegration integration "
            + "where integration.project.id = :projectId and integration.linkType = :linkType")
    Optional<ProjectIntegration> findByProjectIdAndLinkTypeForUpdate(
            @Param("projectId") Long projectId,
            @Param("linkType") LinkType linkType
    );
}
