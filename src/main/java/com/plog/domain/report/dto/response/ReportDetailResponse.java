package com.plog.domain.report.dto.response;

import com.plog.domain.report.entity.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "리포트 상세 응답")
public record ReportDetailResponse(
        @Schema(description = "리포트 ID", example = "20")
        Long reportId,
        @Schema(description = "리포트 표시 코드", example = "PLOG-2026-07-00000020")
        String reportCode,
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
        @Schema(description = "팀 AI 인사이트 - 잘한 점. 생성 실패 시 null",
                example = "팀원들은 각자의 역할을 안정적으로 수행하며, 진행 상황과 의견을 꾸준히 공유해 원활한 협업 흐름을 만들었습니다")
        String teamStrength,
        @Schema(description = "팀 AI 인사이트 - 개선 제안. 생성 실패 시 null",
                example = "앞으로는 초기 단계에서 우선순위와 의사결정 기준을 더욱 명확히 정한다면 협업 효율을 높일 수 있습니다")
        String teamSuggestion,
        @Schema(description = "계산 가능한 멤버 완료율의 단순 평균(0~100)", example = "75.00")
        java.math.BigDecimal teamCompletionRate,
        @Schema(description = "계산 가능한 멤버 마감 준수율의 단순 평균(0~100)", example = "80.00")
        java.math.BigDecimal teamDeadlineComplianceRate,
        @Schema(description = "멤버 요약 목록. 발행 전(GENERATING/FAILED)에는 항상 빈 배열")
        List<ReportMemberSummaryResponse> members
) {
    public ReportDetailResponse(
            Long reportId, Long projectId, String projectName, ReportStatus status,
            Instant completedAt, boolean pdfAvailable, String teamStrength,
            String teamSuggestion, List<ReportMemberSummaryResponse> members
    ) {
        this(reportId, null, projectId, projectName, status, completedAt, pdfAvailable,
                teamStrength, teamSuggestion, null, null, members);
    }
}
