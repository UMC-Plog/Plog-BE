package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectIntegrationRepository extends JpaRepository<ProjectIntegration, Long> {

    List<ProjectIntegration> findAllByProjectIdOrderByLinkTypeAsc(Long projectId);

    Optional<ProjectIntegration> findByProjectIdAndLinkType(Long projectId, LinkType linkType);
}
