package com.plog.domain.project.repository;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findAllByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, MemberStatus status);

    @EntityGraph(attributePaths = {"user"})
    List<ProjectMember> findAllWithUserByProjectId(Long projectId);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"),
            @QueryHint(name = "jakarta.persistence.query.timeout", value = "3000")
    })
    @Query("select member from ProjectMember member "
            + "where member.project.id = :projectId and member.user.id = :userId")
    Optional<ProjectMember> findByProjectIdAndUserIdForUpdate(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

    Optional<ProjectMember> findByProjectIdAndUserIdAndStatus(Long projectId, Long userId, MemberStatus status);

    @EntityGraph(attributePaths = {"user", "project"})
    List<ProjectMember> findAllByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"project"})
    @Query("select member from ProjectMember member "
            + "where member.user.id = :userId and member.status = :memberStatus "
            + "and (:projectStatus is null or member.project.status = :projectStatus) "
            + "order by member.project.updatedAt desc, member.project.id desc")
    Slice<ProjectMember> findProjectSlice(
            @Param("userId") Long userId,
            @Param("memberStatus") MemberStatus memberStatus,
            @Param("projectStatus") ProjectStatus projectStatus,
            Pageable pageable
    );

    @Query("select member from ProjectMember member join fetch member.user "
            + "where member.project.id in :projectIds and member.status = :status "
            + "order by member.project.id asc, member.id asc")
    List<ProjectMember> findActiveMembers(
            @Param("projectIds") List<Long> projectIds,
            @Param("status") MemberStatus status
    );
}
