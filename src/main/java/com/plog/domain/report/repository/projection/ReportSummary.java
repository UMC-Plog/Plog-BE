package com.plog.domain.report.repository.projection;

import com.plog.domain.report.entity.ReportStatus;
import java.time.LocalDateTime;

public interface ReportSummary {

    Long getReportId();

    Long getProjectId();

    String getProjectName();

    ReportStatus getReportStatus();

    LocalDateTime getCompletedAt();
}
