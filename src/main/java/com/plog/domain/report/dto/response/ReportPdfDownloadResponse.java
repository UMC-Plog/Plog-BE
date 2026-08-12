package com.plog.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팀 PDF와 개인 PDF 전체가 포함된 ZIP 다운로드 URL 발급 응답")
public record ReportPdfDownloadResponse(
        @Schema(description = "리포트 ID", example = "20")
        Long reportId,
        @Schema(description = "다운로드될 ZIP 파일명", example = "PLOG-T-2026-08-00000015-reports.zip")
        String fileName,
        @Schema(description = "프론트가 직접 이동할 임시 ZIP 다운로드 URL", example = "https://storage.test/reports.zip")
        String downloadUrl,
        @Schema(description = "다운로드 URL 만료 시간(초)", example = "300")
        long expiresInSeconds
) {
}
