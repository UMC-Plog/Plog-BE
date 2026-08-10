package com.plog.domain.report.repository;

import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.projection.ReportMemberSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportMemberResultRepository extends JpaRepository<ReportMemberResult, Long> {

    Optional<ReportMemberResult> findByReportIdAndProjectMemberId(Long reportId, Long projectMemberId);

    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    List<ReportMemberResult> findAllByReportIdOrderByProjectMemberIdAsc(Long reportId);

    /**
     * 멤버별 결과 상세 조회용. 표시 닉네임을 만들려면 projectMember 와 user 가 둘 다 필요한데,
     * LAZY 라 그냥 조회하면 응답 매핑 시점에 쿼리가 두 번 더 나간다.
     */
    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    Optional<ReportMemberResult> findWithMemberByReportIdAndProjectMemberId(
            Long reportId, Long projectMemberId);

    /**
     * 리포트 상세의 멤버 목록. 엔티티 대신 projection 으로 뽑아 멤버 수만큼의 N+1 을 막는다.
     * <p>
     * 닉네임 규칙은 ProjectMember.getDisplayNickname 과 같은 기준이어야 한다 — 목록에 보이는 이름과
     * 멤버별 결과 API 의 이름이 어긋나면 안 된다.
     * <p>
     * 정렬은 최종 점수 내림차순이되 아직 점수가 없는 멤버(LLM/집계 실패 등)를 뒤로 보낸다.
     * "nulls last" 대신 case 식을 쓰는 건 이 레포지토리의 기존 정렬(findAccessibleReportSlice)과 같은 이유다.
     */
    @Query("select result.projectMember.id as projectMemberId, "
            + "coalesce(nullif(trim(result.projectMember.anNickname), ''), "
            + "result.projectMember.user.nickname) as memberName, "
            + "result.finalScore as finalScore, "
            + "result.contributionRate as contributionRate, "
            + "result.reliabilityTier as reliabilityTier, "
            + "result.headline as headline "
            + "from ReportMemberResult result "
            + "where result.report.id = :reportId "
            + "order by case when result.finalScore is null then 1 else 0 end, "
            + "result.finalScore desc, result.projectMember.id asc")
    List<ReportMemberSummary> findMemberSummaries(@Param("reportId") Long reportId);
}
