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

    @Query("select member.project.id from ProjectMember member where member.id = :memberId")
    Optional<Long> findProjectIdByMemberId(@Param("memberId") Long memberId);

    List<ProjectMember> findAllByProjectId(Long projectId);

    /** 탈퇴 처리용 — 해당 유저의 모든 프로젝트 멤버십(상태 무관). */
    List<ProjectMember> findAllByUserId(Long userId);

    @EntityGraph(attributePaths = {"project"})
    List<ProjectMember> findAllByUserIdAndStatusOrderByIdAsc(Long userId, MemberStatus status);

    long countByProjectIdAndStatus(Long projectId, MemberStatus status);

    long countByProjectIdAndStatusAndFinalSubmittedAtIsNotNull(Long projectId, MemberStatus status);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"),
            @QueryHint(name = "jakarta.persistence.query.timeout", value = "3000")
    })
    @Query("select member from ProjectMember member "
            + "where member.project.id = :projectId and member.user.id = :userId and member.status = :status")
    Optional<ProjectMember> findByProjectIdAndUserIdAndStatusForUpdate(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId,
            @Param("status") MemberStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"),
            @QueryHint(name = "jakarta.persistence.query.timeout", value = "3000")
    })
    @Query("select member from ProjectMember member "
            + "where member.project.id = :projectId and member.id = :memberId and member.status = :status")
    Optional<ProjectMember> findByProjectIdAndIdAndStatusForUpdate(
            @Param("projectId") Long projectId,
            @Param("memberId") Long memberId,
            @Param("status") MemberStatus status
    );

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

    // 채팅 @멘션용
    // 채팅 멘션 매칭용. an_nickname이 없으면(null) user.nickname으로 대체해서 비교한다.
    // 표시 닉네임(anNickname 우선, 없으면 user.nickname) 정책과 동일한 기준으로 맞춰야
    // "화면에 보이는 이름으로 멘션했는데 안 걸리는" 문제가 생기지 않는다.
    // 공백 anNickname도 user.nickname으로 폴백
    @Query("select member from ProjectMember member "
            + "where member.project.id = :projectId and member.status = :status "
            + "and coalesce(nullif(trim(member.anNickname), ''), member.user.nickname) in :nicknames")

    List<ProjectMember> findActiveMembersByProjectIdAndNicknameIn(
            @Param("projectId") Long projectId,
            @Param("status") MemberStatus status,
            @Param("nicknames") Collection<String> nicknames
    );

    // 멘션 가능한 멤버 목록(채팅 @자동완성용). 표시 닉네임 기준(anNickname 우선, 없으면 user.nickname)으로
    // 검색/정렬한다 — 멘션 매칭(findActiveMembersByProjectIdAndNicknameIn)과 동일한 기준을 써야
    // "목록에서 고른 이름"과 "실제 매칭되는 이름"이 어긋나지 않는다.
    // keyword는 null을 절대 넘기면 안 된다. PostgreSQL이 "(:keyword is null or ...)" 형태에서
    // null-only 파라미터의 타입을 추론하지 못해 bytea로 잘못 캐스팅하는 문제가 있었다
    // (lower(bytea) 함수 없음 에러). 호출부(Service)에서 null/blank면 빈 문자열("")로 정규화해서 넘긴다.
    // 빈 문자열이면 LIKE '%%'가 되어 전체 매칭과 동일하게 동작한다.
    @EntityGraph(attributePaths = {"user"})
    @Query("select member from ProjectMember member "
            + "where member.project.id = :projectId and member.status = :status "
            + "and member.id <> :excludeMemberId "
            + "and lower(coalesce(nullif(trim(member.anNickname), ''), member.user.nickname)) "
            + "like lower(concat('%', :keyword, '%')) "
            + "order by coalesce(nullif(trim(member.anNickname), ''), member.user.nickname) asc")
    List<ProjectMember> findMentionableMembers(
            @Param("projectId") Long projectId,
            @Param("status") MemberStatus status,
            @Param("excludeMemberId") Long excludeMemberId,
            @Param("keyword") String keyword
    );

    @EntityGraph(attributePaths = {"user"})
    List<ProjectMember> findAllByProjectIdAndStatusOrderByIdAsc(
            Long projectId,
            MemberStatus status
    );
}
