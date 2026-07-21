package com.plog.domain.report.repository;

import com.plog.domain.report.entity.Report;
import com.plog.domain.report.repository.projection.ReportSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

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
}
