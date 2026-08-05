package com.plog.domain.report.repository;

import com.plog.domain.report.entity.ReportMemberResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportMemberResultRepository extends JpaRepository<ReportMemberResult, Long> {
    Optional<ReportMemberResult> findByReportIdAndProjectMemberId(Long reportId, Long projectMemberId);
}
