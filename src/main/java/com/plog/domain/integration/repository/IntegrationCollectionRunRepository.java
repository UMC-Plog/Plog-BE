package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationCollectionRun;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IntegrationCollectionRunRepository extends JpaRepository<IntegrationCollectionRun, Long> {

    Optional<IntegrationCollectionRun> findByProjectId(Long projectId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into integration_collection_runs "
            + "(project_id, status, attempt_count, created_at, updated_at, version) "
            + "values (:projectId, 'PENDING', 0, current_timestamp, current_timestamp, 0) "
            + "on conflict (project_id) do nothing", nativeQuery = true)
    int createIfAbsent(@Param("projectId") Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from IntegrationCollectionRun run where run.project.id = :projectId")
    Optional<IntegrationCollectionRun> findByProjectIdForUpdate(@Param("projectId") Long projectId);
}
