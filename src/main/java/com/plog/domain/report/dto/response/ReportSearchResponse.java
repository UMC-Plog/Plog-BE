package com.plog.domain.report.dto.response;

import com.plog.domain.report.entity.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "리포트 검색 항목")
public record ReportSearchResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "프로젝트 이름", example = "Plog")
        String projectName,
        @Schema(description = "리포트 ID", example = "20")
        Long reportId,
        @Schema(description = "리포트 상태", example = "COMPLETED")
        ReportStatus reportStatus,
        @Schema(description = "리포트 완료 시각", example = "2026-07-24T13:30:00Z")
        Instant completedAt
) {
}
