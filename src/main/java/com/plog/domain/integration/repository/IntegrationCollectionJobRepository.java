package com.plog.domain.integration.repository;

import com.plog.domain.integration.entity.IntegrationCollectionJob;
import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationCollectionJobRepository extends JpaRepository<IntegrationCollectionJob, Long> {

    @Query("select job from IntegrationCollectionJob job "
            + "where job.project.id = :projectId and job.status in :statuses "
            + "order by job.id desc")
    List<IntegrationCollectionJob> findByProjectIdAndStatuses(
            @Param("projectId") Long projectId,
            @Param("statuses") Collection<IntegrationCollectionJobStatus> statuses
    );

    @Query("select job from IntegrationCollectionJob job "
            + "where job.project.id = :projectId order by job.id desc")
    List<IntegrationCollectionJob> findLatestByProjectId(
            @Param("projectId") Long projectId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from IntegrationCollectionJob job "
            + "where job.status in :statuses and job.availableAt <= :now "
            + "order by job.availableAt asc, job.id asc")
    List<IntegrationCollectionJob> findDueForUpdate(
            @Param("statuses") Collection<IntegrationCollectionJobStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from IntegrationCollectionJob job where job.id = :id")
    Optional<IntegrationCollectionJob> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from IntegrationCollectionJob job "
            + "where job.status = :status and job.heartbeatAt < :staleBefore order by job.id asc")
    List<IntegrationCollectionJob> findStaleForUpdate(
            @Param("status") IntegrationCollectionJobStatus status,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable
    );
}
