package com.plog.domain.report.dto.response;

public record ReportPdfDownloadResponse(
        Long reportId,
        String fileName,
        String downloadUrl,
        long expiresInSeconds
) {
}
