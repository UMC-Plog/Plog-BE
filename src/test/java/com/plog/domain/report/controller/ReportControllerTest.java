package com.plog.domain.report.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.report.dto.response.ReportDetailResponse;
import com.plog.domain.report.dto.response.ReportMemberResultResponse;
import com.plog.domain.report.dto.response.ReportMemberSummaryResponse;
import com.plog.domain.report.dto.response.ReportSearchResponse;
import com.plog.domain.report.dto.response.ReportPdfDownloadResponse;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.service.ReportDetailService;
import com.plog.domain.report.service.ReportGenerationLauncher;
import com.plog.domain.report.service.ReportPdfDownloadService;
import com.plog.domain.report.service.ReportSearchService;
import java.math.BigDecimal;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.SliceResponse;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaTokenProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportSearchService service;

    @MockitoBean
    private ReportDetailService detailService;

    @MockitoBean
    private ReportGenerationLauncher generationLauncher;

    @MockitoBean
    private ReportPdfDownloadService pdfDownloadService;

    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MediaTokenProvider mediaTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getsReportsForTheRawLongPrincipalWithDefaultPaging() throws Exception {
        authenticate(1L);
        Instant completedAt = Instant.parse("2026-07-20T12:00:00Z");
        SliceResponse<ReportSearchResponse> response = new SliceResponse<>(
                List.of(new ReportSearchResponse(
                        10L,
                        "Plog",
                        20L,
                        ReportStatus.COMPLETED,
                        completedAt
                )),
                0,
                20,
                false
        );
        given(service.getReports(1L, 0, 20)).willReturn(response);

        mockMvc.perform(get("/api/dashboard/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT001"))
                .andExpect(jsonPath("$.result.content[0].projectId").value(10L))
                .andExpect(jsonPath("$.result.content[0].projectName").value("Plog"))
                .andExpect(jsonPath("$.result.content[0].reportId").value(20L))
                .andExpect(jsonPath("$.result.content[0].reportStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.content[0].completedAt")
                        .value("2026-07-20T12:00:00Z"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.totalElements").doesNotExist());
    }

    @Test
    void searchesReportsForTheRawLongPrincipalWithDefaultPaging() throws Exception {
        authenticate(1L);
        Instant completedAt = Instant.parse("2026-07-20T12:00:00Z");
        SliceResponse<ReportSearchResponse> response = new SliceResponse<>(
                List.of(new ReportSearchResponse(
                        10L,
                        "Plog",
                        20L,
                        ReportStatus.COMPLETED,
                        completedAt
                )),
                0,
                20,
                false
        );
        given(service.search(
                1L,
                "plog",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                0,
                20
        )).willReturn(response);

        mockMvc.perform(get("/api/dashboard/reports/search")
                        .param("keyword", "plog")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT002"))
                .andExpect(jsonPath("$.result.content[0].projectId").value(10L))
                .andExpect(jsonPath("$.result.content[0].projectName").value("Plog"))
                .andExpect(jsonPath("$.result.content[0].reportId").value(20L))
                .andExpect(jsonPath("$.result.content[0].reportStatus").value("COMPLETED"))
                // 오프셋을 실어 보낸다 — 클라이언트가 서버 타임존을 추측하지 않도록.
                .andExpect(jsonPath("$.result.content[0].completedAt")
                        .value("2026-07-20T12:00:00Z"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.totalElements").doesNotExist());
    }

    @Test
    void createsAReportPdfDownloadUrlForTheRawLongPrincipal() throws Exception {
        authenticate(1L);
        given(pdfDownloadService.createDownloadUrl(1L, 20L))
                .willReturn(new ReportPdfDownloadResponse(
                        20L,
                        "Plog-report.pdf",
                        "https://storage.test/report.pdf",
                        300
                ));

        mockMvc.perform(get("/api/dashboard/reports/20/pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT006"))
                .andExpect(jsonPath("$.result.reportId").value(20L))
                .andExpect(jsonPath("$.result.fileName").value("Plog-report.pdf"))
                .andExpect(jsonPath("$.result.downloadUrl")
                        .value("https://storage.test/report.pdf"))
                .andExpect(jsonPath("$.result.expiresInSeconds").value(300));
    }

    @Test
    void returnsTheReportNotFoundErrorForPdfDownload() throws Exception {
        authenticate(1L);
        given(pdfDownloadService.createDownloadUrl(1L, 999L))
                .willThrow(new ApiException(ReportErrorCode.REPORT_NOT_FOUND));

        mockMvc.perform(get("/api/dashboard/reports/999/pdf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT003"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsAnInvalidReportIdBeforeCallingTheService(String reportId) throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/dashboard/reports/{reportId}/pdf", reportId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(pdfDownloadService);
    }

    @Test
    void returnsTheReportDateRangeError() throws Exception {
        authenticate(1L);
        given(service.search(
                1L,
                "",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 31),
                0,
                20
        )).willThrow(new ApiException(ReportErrorCode.INVALID_DATE_RANGE));

        mockMvc.perform(get("/api/dashboard/reports/search")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT002"));
    }

    @Test
    void rejectsAnInvalidPageBeforeCallingTheService() throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/dashboard/reports/search").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    void rejectsAnInvalidSizeBeforeCallingTheService(String size) throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/dashboard/reports/search").param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAMalformedDateBeforeCallingTheService() throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/dashboard/reports/search")
                        .param("startDate", "2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(service);
    }

    @Test
    void getsReportDetailWithMemberSummaries() throws Exception {
        authenticate(1L);
        given(detailService.getReport(1L, 20L)).willReturn(new ReportDetailResponse(
                20L,
                10L,
                "Plog",
                ReportStatus.COMPLETED,
                Instant.parse("2026-07-20T12:00:00Z"),
                true,
                List.of(new ReportMemberSummaryResponse(
                        7L,
                        "창훈",
                        new BigDecimal("82.50"),
                        ReliabilityTier.P1
                ))
        ));

        mockMvc.perform(get("/api/dashboard/reports/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT003"))
                .andExpect(jsonPath("$.result.reportId").value(20L))
                .andExpect(jsonPath("$.result.projectName").value("Plog"))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.completedAt").value("2026-07-20T12:00:00Z"))
                .andExpect(jsonPath("$.result.pdfAvailable").value(true))
                .andExpect(jsonPath("$.result.members[0].projectMemberId").value(7L))
                .andExpect(jsonPath("$.result.members[0].memberName").value("창훈"))
                .andExpect(jsonPath("$.result.members[0].finalScore").value(82.50))
                .andExpect(jsonPath("$.result.members[0].reliabilityTier").value("P1"));
    }

    // 발행 전에도 404가 아니라 200 + 상태다 — 프론트가 "생성 중" 화면을 그리고 폴링할 수 있어야 한다.
    @Test
    void returnsGeneratingReportWithEmptyMembersInsteadOfNotFound() throws Exception {
        authenticate(1L);
        given(detailService.getReport(1L, 20L)).willReturn(new ReportDetailResponse(
                20L, 10L, "Plog", ReportStatus.GENERATING, null, false, List.of()
        ));

        mockMvc.perform(get("/api/dashboard/reports/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT003"))
                .andExpect(jsonPath("$.result.status").value("GENERATING"))
                .andExpect(jsonPath("$.result.pdfAvailable").value(false))
                .andExpect(jsonPath("$.result.members").isEmpty())
                .andExpect(jsonPath("$.result.completedAt").doesNotExist());
    }

    @Test
    void getsMemberResult() throws Exception {
        authenticate(1L);
        given(detailService.getMemberResult(1L, 20L, 7L)).willReturn(new ReportMemberResultResponse(
                20L,
                7L,
                "창훈",
                new BigDecimal("88.00"),
                null,
                new BigDecimal("80.00"),
                new BigDecimal("70.00"),
                new BigDecimal("82.50"),
                false,
                ReliabilityTier.P2,
                "Notion이 연동되지 않아 일부 작업 과정은 반영되지 않았을 수 있습니다."
        ));

        mockMvc.perform(get("/api/dashboard/reports/20/members/7/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT004"))
                .andExpect(jsonPath("$.result.projectMemberId").value(7L))
                .andExpect(jsonPath("$.result.memberName").value("창훈"))
                .andExpect(jsonPath("$.result.finalScore").value(82.50))
                .andExpect(jsonPath("$.result.externalToolConnected").value(false))
                .andExpect(jsonPath("$.result.reliabilityTier").value("P2"))
                // 미연동이면 externalScore 는 아예 내려가지 않는다 (0점으로 오해되면 안 된다)
                .andExpect(jsonPath("$.result.externalScore").doesNotExist());
    }

    @Test
    void returnsTheMemberResultNotFoundError() throws Exception {
        authenticate(1L);
        given(detailService.getMemberResult(1L, 20L, 999L))
                .willThrow(new ApiException(ReportErrorCode.REPORT_MEMBER_RESULT_NOT_FOUND));

        mockMvc.perform(get("/api/dashboard/reports/20/members/999/result"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT006"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsAnInvalidReportIdOnDetailBeforeCallingTheService(String reportId) throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/dashboard/reports/{reportId}", reportId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(detailService);
    }

    // /search 는 /{reportId} 보다 먼저 매칭되어야 한다 — 상세 API 추가로 검색이 가려지면 안 된다.
    @Test
    void keepsTheSearchPathRoutedToTheSearchService() throws Exception {
        authenticate(1L);
        given(service.search(1L, "", null, null, 0, 20)).willReturn(
                new SliceResponse<>(List.of(), 0, 20, false));

        mockMvc.perform(get("/api/dashboard/reports/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT002"));

        verifyNoInteractions(detailService);
    }

    @Test
    void acceptsReportGenerationWithoutWaitingForCompletion() throws Exception {
        authenticate(1L);

        mockMvc.perform(post("/api/dashboard/reports/20/generate").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("REPORT005"))
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(generationLauncher).launch(1L, 20L);
    }

    @Test
    void returnsConflictWhenReportIsAlreadyResolved() throws Exception {
        authenticate(1L);
        org.mockito.BDDMockito.willThrow(new ApiException(ReportErrorCode.REPORT_ALREADY_RESOLVED))
                .given(generationLauncher).launch(1L, 20L);

        mockMvc.perform(post("/api/dashboard/reports/20/generate").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT007"));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }
}
