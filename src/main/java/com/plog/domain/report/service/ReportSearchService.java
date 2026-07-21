package com.plog.domain.report.service;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.report.dto.response.ReportSearchResponse;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.report.repository.projection.ReportSummary;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.SliceResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportSearchService {

    private static final int MAX_KEYWORD_LENGTH = 20;

    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public SliceResponse<ReportSearchResponse> search(
            Long userId,
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        validateDateRange(startDate, endDate);
        String projectNamePattern = toSearchPattern(keyword);
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        Slice<ReportSummary> reportSlice = reportRepository.searchAccessibleReportSlice(
                userId,
                MemberStatus.ACTIVE,
                projectNamePattern,
                startDate != null,
                startAt,
                endDate != null,
                endExclusive,
                PageRequest.of(page, size)
        );
        List<ReportSearchResponse> content = reportSlice.getContent().stream()
                .map(this::toResponse)
                .toList();
        return SliceResponse.of(reportSlice, content);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ApiException(ReportErrorCode.INVALID_DATE_RANGE);
        }
    }

    private String toSearchPattern(String keyword) {
        if (keyword == null) {
            throw new ApiException(ReportErrorCode.INVALID_SEARCH_KEYWORD);
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new ApiException(ReportErrorCode.INVALID_SEARCH_KEYWORD);
        }
        if (normalized.isEmpty()) {
            return "%";
        }
        String escaped = normalized.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private ReportSearchResponse toResponse(ReportSummary summary) {
        return new ReportSearchResponse(
                summary.getProjectId(),
                summary.getProjectName(),
                summary.getReportId(),
                summary.getReportStatus(),
                summary.getCompletedAt()
        );
    }
}
