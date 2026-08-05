package com.plog.domain.report.dto.response;

import com.plog.domain.report.entity.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "리포트 상세 응답")
public record ReportDetailResponse(
        @Schema(description = "리포트 ID", example = "20")
        Long reportId,
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "프로젝트 이름", example = "Plog")
        String projectName,
        @Schema(description = "리포트 상태", example = "COMPLETED")
        ReportStatus status,
        @Schema(description = "리포트 완료 시각. 발행 전이면 null", example = "2026-07-24T13:30:00Z")
        Instant completedAt,
        @Schema(description = "PDF 다운로드 URL 발급이 가능한지", example = "true")
        boolean pdfAvailable,
        @Schema(description = "멤버 요약 목록. 발행 전(GENERATING/FAILED)에는 항상 빈 배열")
        List<ReportMemberSummaryResponse> members
) {
}
