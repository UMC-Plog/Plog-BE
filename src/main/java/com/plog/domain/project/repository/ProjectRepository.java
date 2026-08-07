package com.plog.domain.project.repository;

import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.project.entity.Project;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByInviteTokenHash(String inviteTokenHash);

    boolean existsByInviteTokenHash(String inviteTokenHash);

    @Query(value = "select pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
    void acquireInviteTokenCandidateLock(@Param("lockKey") long lockKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from Project project where project.id = :projectId")
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);

    @Query("select project from Project project "
            + "where project.status = com.plog.domain.project.entity.ProjectStatus.IN_PROGRESS "
            + "and project.endDay <= :today "
            + "and not exists (select 1 from Notification notification "
            + "where notification.project = project and notification.type = :type) "
            + "order by project.endDay asc, project.id asc")
    List<Project> findProjectsAwaitingPeerEvaluationNotification(
            @Param("today") LocalDate today,
            @Param("type") NotificationType type,
            Pageable pageable
    );
}
