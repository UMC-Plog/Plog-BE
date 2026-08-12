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
        @Schema(description = "팀 리포트 표시 코드", example = "PLOG-T-2026-08-00000015")
        String reportCode,
        @Schema(description = "리포트 상태", example = "COMPLETED")
        ReportStatus reportStatus,
        @Schema(description = "리포트 완료 시각", example = "2026-07-24T13:30:00Z")
        Instant completedAt,
        @Schema(description = "PDF 다운로드 URL 발급이 가능한지. 리포트 상세 응답의 같은 필드와 동일한 기준이다 "
                + "— reportStatus가 COMPLETED여도 ZIP이 없으면 false다",
                example = "true")
        boolean pdfAvailable
) {
    public ReportSearchResponse(
            Long projectId, String projectName, Long reportId,
            ReportStatus reportStatus, Instant completedAt, boolean pdfAvailable
    ) {
        this(projectId, projectName, reportId, null, reportStatus, completedAt, pdfAvailable);
    }
}
