package com.plog.domain.project.repository;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findAllByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"user"})
    List<ProjectMember> findAllWithUserByProjectId(Long projectId);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from ProjectMember member "
            + "where member.project.id = :projectId and member.user.id = :userId")
    Optional<ProjectMember> findByProjectIdAndUserIdForUpdate(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

    Optional<ProjectMember> findByProjectIdAndUserIdAndStatus(Long projectId, Long userId, MemberStatus status);
}
