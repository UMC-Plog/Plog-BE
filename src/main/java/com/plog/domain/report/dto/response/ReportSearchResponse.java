package com.plog.domain.report.dto.response;

import com.plog.domain.report.entity.ReportStatus;
import java.time.LocalDateTime;

public record ReportSearchResponse(
        Long projectId,
        String projectName,
        Long reportId,
        ReportStatus reportStatus,
        LocalDateTime completedAt
) {
}
