package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.report.dto.response.ReportSearchResponse;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.report.repository.projection.ReportSummary;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.SliceResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

@ExtendWith(MockitoExtension.class)
class ReportSearchServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportSummary summary;

    private ReportSearchService service;

    @BeforeEach
    void setUp() {
        service = new ReportSearchService(reportRepository);
    }

    @Test
    void normalizesFiltersAndMapsAnAccessibleReportSlice() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 20, 12, 0);
        given(summary.getProjectId()).willReturn(10L);
        given(summary.getProjectName()).willReturn("PLOG API");
        given(summary.getReportId()).willReturn(20L);
        given(summary.getReportStatus()).willReturn(ReportStatus.COMPLETED);
        given(summary.getCompletedAt()).willReturn(completedAt);
        given(reportRepository.searchAccessibleReportSlice(
                1L,
                MemberStatus.ACTIVE,
                "%plog!%!_!!%",
                true,
                // 필터는 KST 달력 하루 기준 → UTC 저장값으로 변환해 넘긴다.
                LocalDateTime.of(2026, 6, 30, 15, 0),
                true,
                LocalDateTime.of(2026, 7, 31, 15, 0),
                PageRequest.of(0, 2)
        )).willReturn(new SliceImpl<>(List.of(summary), PageRequest.of(0, 2), true));

        SliceResponse<ReportSearchResponse> response = service.search(
                1L,
                "  PLOG%_!  ",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                0,
                2
        );

        assertThat(response.content()).containsExactly(new ReportSearchResponse(
                10L,
                "PLOG API",
                20L,
                ReportStatus.COMPLETED,
                completedAt.toInstant(ZoneOffset.UTC)
        ));
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    void supportsBlankKeywordAndOneSidedDates() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(reportRepository.searchAccessibleReportSlice(
                1L,
                MemberStatus.ACTIVE,
                "%",
                false,
                null,
                true,
                LocalDateTime.of(2026, 7, 31, 15, 0),
                pageable
        )).willReturn(new SliceImpl<>(List.of(), pageable, false));

        SliceResponse<ReportSearchResponse> response = service.search(
                1L,
                "   ",
                null,
                LocalDate.of(2026, 7, 31),
                0,
                20
        );

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        verify(reportRepository).searchAccessibleReportSlice(
                1L,
                MemberStatus.ACTIVE,
                "%",
                false,
                null,
                true,
                LocalDateTime.of(2026, 7, 31, 15, 0),
                pageable
        );
    }

    @Test
    void validatesKeywordLengthAfterTrimming() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(reportRepository.searchAccessibleReportSlice(
                1L,
                MemberStatus.ACTIVE,
                "%12345678901234567890%",
                false,
                null,
                false,
                null,
                pageable
        )).willReturn(new SliceImpl<>(List.of(), pageable, false));

        SliceResponse<ReportSearchResponse> response = service.search(
                1L,
                "  12345678901234567890  ",
                null,
                null,
                0,
                20
        );

        assertThat(response.content()).isEmpty();
        verify(reportRepository).searchAccessibleReportSlice(
                1L,
                MemberStatus.ACTIVE,
                "%12345678901234567890%",
                false,
                null,
                false,
                null,
                pageable
        );
    }

    @Test
    void rejectsAReversedDateRange() {
        assertThatThrownBy(() -> service.search(
                1L,
                "plog",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 31),
                0,
                20
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.INVALID_DATE_RANGE));

        verifyNoInteractions(reportRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"123456789012345678901"})
    void rejectsAnInvalidKeyword(String keyword) {
        assertThatThrownBy(() -> service.search(1L, keyword, null, null, 0, 20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.INVALID_SEARCH_KEYWORD));

        verifyNoInteractions(reportRepository);
    }

    @Test
    void rejectsANullPrincipalBeforeSearching() {
        assertThatThrownBy(() -> service.search(null, "", null, null, 0, 20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));

        verifyNoInteractions(reportRepository);
    }
}
