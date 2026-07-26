package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationCollectionRun;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationCollectionRunRepository extends JpaRepository<IntegrationCollectionRun, Long> {

    Optional<IntegrationCollectionRun> findByProjectId(Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from IntegrationCollectionRun run where run.project.id = :projectId")
    Optional<IntegrationCollectionRun> findByProjectIdForUpdate(@Param("projectId") Long projectId);
}
