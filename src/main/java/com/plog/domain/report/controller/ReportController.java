package com.plog.domain.report.controller;

import com.plog.domain.report.controller.docs.ReportControllerDoc;
import com.plog.domain.report.dto.response.ReportPdfDownloadResponse;
import com.plog.domain.report.dto.response.ReportSearchResponse;
import com.plog.domain.report.service.ReportPdfDownloadService;
import com.plog.domain.report.service.ReportSearchService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ReportSuccessCode;
import com.plog.global.api.response.SliceResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard/reports")
public class ReportController implements ReportControllerDoc {

    private final ReportSearchService reportSearchService;
    private final ReportPdfDownloadService reportPdfDownloadService;

    @Override
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<SliceResponse<ReportSearchResponse>>> searchReports(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        SliceResponse<ReportSearchResponse> response = reportSearchService.search(
                userId,
                keyword,
                startDate,
                endDate,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(ReportSuccessCode.REPORT_SEARCHED, response));
    }

    @Override
    @GetMapping("/{reportId}/pdf")
    public ResponseEntity<ApiResponse<ReportPdfDownloadResponse>> createPdfDownloadUrl(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reportId
    ) {
        ReportPdfDownloadResponse response = reportPdfDownloadService.createDownloadUrl(
                userId,
                reportId
        );
        return ResponseEntity.ok(ApiResponse.success(
                ReportSuccessCode.REPORT_PDF_DOWNLOAD_URL_ISSUED,
                response
        ));
    }
}
