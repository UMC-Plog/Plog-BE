package com.plog.domain.project.repository;

import com.plog.domain.integration.entity.ExternalConnection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectExternalConnectionRepository extends JpaRepository<ExternalConnection, Long> {
    List<ExternalConnection> findAllByProjectMemberIdOrderByLinkTypeAsc(Long projectMemberId);
}
