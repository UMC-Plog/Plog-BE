package com.plog.domain.report.repository;

import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.projection.ReportMemberSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportMemberResultRepository extends JpaRepository<ReportMemberResult, Long> {

    Optional<ReportMemberResult> findByReportIdAndProjectMemberId(Long reportId, Long projectMemberId);

    /**
     * 발행은 끝났지만 멤버별 PDF ZIP이 하나라도 없는 리포트를 복구 순서대로 찾는다.
     * reportId 커서를 사용해 같은 실행에서 실패한 리포트를 무한 재조회하지 않는다.
     */
    @Query("select distinct result.report.id from ReportMemberResult result "
            + "where result.report.status = com.plog.domain.report.entity.ReportStatus.COMPLETED "
            + "and result.report.id > :afterReportId "
            + "and (result.pdfObjectKey is null or result.pdfObjectKey = '' "
            + "or result.pdfFileName is null or result.pdfFileName = '') "
            + "order by result.report.id asc")
    List<Long> findCompletedReportIdsMissingPdfAfter(
            @Param("afterReportId") Long afterReportId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    List<ReportMemberResult> findAllByReportIdOrderByProjectMemberIdAsc(Long reportId);

    /**
     * 멤버별 결과 상세 조회용. 실명을 만들려면 projectMember 와 user 가 둘 다 필요한데,
     * LAZY 라 그냥 조회하면 응답 매핑 시점에 쿼리가 두 번 더 나간다.
     */
    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    Optional<ReportMemberResult> findWithMemberByReportIdAndProjectMemberId(
            Long reportId, Long projectMemberId);

    /**
     * 리포트 상세의 멤버 목록. 엔티티 대신 projection 으로 뽑아 멤버 수만큼의 N+1 을 막는다.
     * <p>
     * 리포트는 사용자 실명을 공개하는 화면이므로 User.name 을 memberName 으로 사용한다.
     * 일반 프로젝트 화면의 익명/계정 닉네임 표시 규칙에는 영향을 주지 않는다.
     * <p>
     * 정렬은 최종 점수 내림차순이되 아직 점수가 없는 멤버(LLM/집계 실패 등)를 뒤로 보낸다.
     * "nulls last" 대신 case 식을 쓰는 건 이 레포지토리의 기존 정렬(findAccessibleReportSlice)과 같은 이유다.
     */
    @Query("select result.projectMember.id as projectMemberId, "
            + "result.projectMember.user.name as memberName, "
            + "result.finalScore as finalScore, "
            + "result.contributionRate as contributionRate, "
            + "result.reliabilityTier as reliabilityTier, "
            + "result.projectMember.user.profilePreset as profilePreset, "
            + "result.totalTaskCount as totalTaskCount, "
            + "result.completedTaskCount as completedTaskCount, "
            + "result.deadlineMetTaskCount as deadlineMetTaskCount, "
            + "result.deadlineTargetTaskCount as deadlineTargetTaskCount, "
            + "result.completionRate as completionRate, "
            + "result.deadlineComplianceRate as deadlineComplianceRate, "
            + "result.peerAverage as peerAverage, "
            + "result.competencyScores as competencyScores, "
            + "result.peerKeywords as peerKeywords, "
            + "coalesce(result.teamMemberHeadline, result.headline) as headline "
            + "from ReportMemberResult result "
            + "where result.report.id = :reportId "
            + "order by case when result.finalScore is null then 1 else 0 end, "
            + "result.finalScore desc, result.projectMember.id asc")
    List<ReportMemberSummary> findMemberSummaries(@Param("reportId") Long reportId);
}
