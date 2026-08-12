package com.plog.domain.report.service;

import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.dto.response.ReportPdfDownloadResponse;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.ReportMemberResultRepository;
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
    private final ReportMemberResultRepository resultRepository;
    private final ProjectAccessService projectAccessService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public ReportPdfDownloadResponse createDownloadUrl(Long userId, Long reportId) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        ProjectMember requester = projectAccessService.requireActiveMember(report.getProject().getId(), userId);
        if (report.getStatus() != ReportStatus.COMPLETED) {
            throw new ApiException(ReportErrorCode.REPORT_NOT_COMPLETED);
        }
        ReportMemberResult result = resultRepository
                .findByReportIdAndProjectMemberId(reportId, requester.getId())
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_PDF_NOT_FOUND));
        if (result.getPdfObjectKey() == null || result.getPdfObjectKey().isBlank()
                || result.getPdfFileName() == null || result.getPdfFileName().isBlank()) {
            throw new ApiException(ReportErrorCode.REPORT_PDF_NOT_FOUND);
        }

        FileStorageDto.PresignedDownloadResponse presigned = fileStorageService.createDownloadUrl(
                result.getPdfObjectKey(),
                result.getPdfFileName(),
                DOWNLOAD_URL_DURATION
        );
        return new ReportPdfDownloadResponse(
                reportId,
                result.getPdfFileName(),
                presigned.downloadUrl(),
                presigned.expiresInSeconds()
        );
    }
}
