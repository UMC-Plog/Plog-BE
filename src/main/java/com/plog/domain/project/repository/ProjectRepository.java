package com.plog.domain.project.repository;

import com.plog.domain.project.entity.Project;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByInviteTokenHash(String inviteTokenHash);

    boolean existsByInviteTokenHash(String inviteTokenHash);

    @Query(value = "select pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
    void acquireInviteTokenCandidateLock(@Param("lockKey") long lockKey);
}
