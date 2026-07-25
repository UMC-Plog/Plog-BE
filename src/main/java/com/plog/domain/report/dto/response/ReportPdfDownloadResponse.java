package com.plog.domain.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리포트 PDF 다운로드 URL 발급 응답")
public record ReportPdfDownloadResponse(
        @Schema(description = "리포트 ID", example = "20")
        Long reportId,
        @Schema(description = "다운로드될 PDF 파일명", example = "Plog-report.pdf")
        String fileName,
        @Schema(description = "프론트가 직접 이동하거나 새 창으로 열 수 있는 임시 다운로드 URL", example = "https://storage.test/report.pdf")
        String downloadUrl,
        @Schema(description = "다운로드 URL 만료 시간(초)", example = "300")
        long expiresInSeconds
) {
}
