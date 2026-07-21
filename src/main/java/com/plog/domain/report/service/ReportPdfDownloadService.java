package com.plog.domain.report.service;

import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.dto.response.ReportPdfDownloadResponse;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.FileStorageDto;
import com.plog.infrastructure.s3.FileStorageService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportPdfDownloadService {

    private static final Duration DOWNLOAD_URL_DURATION = Duration.ofSeconds(300);

    private final ReportRepository reportRepository;
    private final ProjectAccessService projectAccessService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public ReportPdfDownloadResponse createDownloadUrl(Long userId, Long reportId) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        projectAccessService.requireActiveMember(report.getProject().getId(), userId);
        if (report.getStatus() != ReportStatus.COMPLETED) {
            throw new ApiException(ReportErrorCode.REPORT_NOT_COMPLETED);
        }
        if (report.getPdfObjectKey() == null || report.getPdfObjectKey().isBlank()
                || report.getPdfFileName() == null || report.getPdfFileName().isBlank()) {
            throw new ApiException(ReportErrorCode.REPORT_PDF_NOT_FOUND);
        }

        FileStorageDto.PresignedDownloadResponse presigned = fileStorageService.createDownloadUrl(
                report.getPdfObjectKey(),
                DOWNLOAD_URL_DURATION
        );
        return new ReportPdfDownloadResponse(
                reportId,
                report.getPdfFileName(),
                presigned.downloadUrl(),
                presigned.expiresInSeconds()
        );
    }
}
