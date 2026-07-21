package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.ExternalConnection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalConnectionRepository extends JpaRepository<ExternalConnection, Long> {

    List<ExternalConnectionSummary> findAllByProjectMemberId(Long projectMemberId);
}
