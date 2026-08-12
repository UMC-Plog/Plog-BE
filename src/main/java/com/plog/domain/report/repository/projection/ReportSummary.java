package com.plog.domain.report.repository.projection;

import com.plog.domain.report.entity.ReportStatus;
import java.time.LocalDateTime;

public interface ReportSummary {

    Long getReportId();

    Long getProjectId();

    String getProjectName();

    ReportStatus getReportStatus();

    LocalDateTime getCompletedAt();

    LocalDateTime getCreatedAt();

    /** PDF ZIP 아카이브의 S3 키. 업로드 전이거나 업로드가 실패했으면 null 이다. */
    String getPdfObjectKey();

    String getPdfFileName();
}
