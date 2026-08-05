package com.plog.domain.report.repository;

import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.projection.ReportSummary;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 리포트 생성 멱등성 체크용. reports 는 project_id 유니크가 아니라(중간/최종 리포트 대비)
     * DB 제약으로 중복을 막을 수 없어서, "재시작을 막는 상태"의 행이 이미 있는지로 판정한다.
     * 기준 집합은 {@link ReportStatus#restartBlockingStatuses()}.
     */
    boolean existsByProjectIdAndStatusIn(Long projectId, Collection<ReportStatus> statuses);

    /** 상세 조회용. 권한 확인과 응답 모두 project 가 필요해서 LAZY 프록시를 미리 채워 온다. */
    @EntityGraph(attributePaths = {"project"})
    Optional<Report> findWithProjectById(Long reportId);

    /**
     * 리포트 자동 생성 배치의 대상 — 평가 유예가 끝났는데 아직 리포트가 없는 프로젝트.
     * <p>
     * 리포트 도메인의 배치가 쓰는 쿼리라 ProjectRepository 가 아니라 여기에 둔다
     * (report → project 방향은 이미 Report.project 로 존재한다).
     * <p>
     * {@code endDayOnOrBefore} 는 {@code Project.latestEndDayWithClosedEvaluation(today)} 를 넘긴다 —
     * 유예 기간을 여기에 다시 적으면 엔티티의 규칙과 어긋날 수 있다.
     * 정렬은 오래 밀린 프로젝트부터 — batchSize 로 잘려도 순서가 안정적이어야 다음 회차에 나머지가 처리된다.
     */
    @Query("select project from Project project "
            + "where project.endDay <= :endDayOnOrBefore "
            + "and not exists (select 1 from Report report "
            + "where report.project = project and report.status in :activeStatuses) "
            + "order by project.endDay asc, project.id asc")
    List<Project> findProjectsDueForReport(
            @Param("endDayOnOrBefore") LocalDate endDayOnOrBefore,
            @Param("activeStatuses") Collection<ReportStatus> activeStatuses,
            Pageable pageable
    );

    @Query("select report.id as reportId, "
            + "report.project.id as projectId, "
            + "report.project.projectName as projectName, "
            + "report.status as reportStatus, "
            + "report.completedAt as completedAt "
            + "from Report report "
            + "where report.project.id = :projectId "
            + "order by case when report.completedAt is null then 1 else 0 end, "
            + "report.completedAt desc, report.id desc")
    Slice<ReportSummary> findProjectReportSlice(
            @Param("projectId") Long projectId,
            Pageable pageable
    );

    @Query("select report.id as reportId, "
            + "report.project.id as projectId, "
            + "report.project.projectName as projectName, "
            + "report.status as reportStatus, "
            + "report.completedAt as completedAt "
            + "from Report report "
            + "where exists (select 1 from ProjectMember member "
            + "where member.project = report.project "
            + "and member.user.id = :userId "
            + "and member.status = :memberStatus) "
            + "order by case when report.completedAt is null then 1 else 0 end, "
            + "report.completedAt desc, report.id desc")
    Slice<ReportSummary> findAccessibleReportSlice(
            @Param("userId") Long userId,
            @Param("memberStatus") MemberStatus memberStatus,
            Pageable pageable
    );

    @Query("select report.id as reportId, "
            + "report.project.id as projectId, "
            + "report.project.projectName as projectName, "
            + "report.status as reportStatus, "
            + "report.completedAt as completedAt "
            + "from Report report "
            + "where exists (select 1 from ProjectMember member "
            + "where member.project = report.project "
            + "and member.user.id = :userId "
            + "and member.status = :memberStatus) "
            + "and lower(report.project.projectName) like :projectNamePattern escape '!' "
            + "and (:hasStartDate = false or report.completedAt >= :startAt) "
            + "and (:hasEndDate = false or report.completedAt < :endExclusive) "
            + "order by case when report.completedAt is null then 1 else 0 end, "
            + "report.completedAt desc, report.id desc")
    Slice<ReportSummary> searchAccessibleReportSlice(
            @Param("userId") Long userId,
            @Param("memberStatus") MemberStatus memberStatus,
            @Param("projectNamePattern") String projectNamePattern,
            @Param("hasStartDate") boolean hasStartDate,
            @Param("startAt") LocalDateTime startAt,
            @Param("hasEndDate") boolean hasEndDate,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable
    );
}
