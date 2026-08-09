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
import com.plog.domain.report.llm.MemberReportText;
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
                "팀이 잘한 점",
                "앞으로는 이렇게 해보세요",
                List.of(new ReportMemberSummaryResponse(
                        7L,
                        "창훈",
                        new BigDecimal("82.50"),
                        ReliabilityTier.P1,
                        "적극적인 리더십으로 팀의 방향을 잡았어요"
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
                .andExpect(jsonPath("$.result.members[0].reliabilityTier").value("P1"))
                // 팀 리포트 화면이 필요한 AI 텍스트가 실제로 내려가는지
                .andExpect(jsonPath("$.result.teamStrength").value("팀이 잘한 점"))
                .andExpect(jsonPath("$.result.teamSuggestion").value("앞으로는 이렇게 해보세요"))
                .andExpect(jsonPath("$.result.members[0].headline")
                        .value("적극적인 리더십으로 팀의 방향을 잡았어요"));
    }

    // 발행 전에도 404가 아니라 200 + 상태다 — 프론트가 "생성 중" 화면을 그리고 폴링할 수 있어야 한다.
    @Test
    void returnsGeneratingReportWithEmptyMembersInsteadOfNotFound() throws Exception {
        authenticate(1L);
        given(detailService.getReport(1L, 20L)).willReturn(new ReportDetailResponse(
                20L, 10L, "Plog", ReportStatus.GENERATING, null, false, null, null, List.of()
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
                true,
                ReliabilityTier.P2,
                "외부 도구는 연동됐지만 이 멤버의 점수화 가능한 활동이 부족합니다.",
                13,
                12,
                11,
                "적극적인 리더십으로 팀의 방향을 잡았어요",
                List.of(new MemberReportText.StrengthCard("주도성", "일정을 주도적으로 관리하고 실행해요")),
                new MemberReportText.Weakness("의견 제시 빈도가 낮음", List.of("의견 제시를 늘려보세요")),
                new MemberReportText.GrowthInsight("성장", "유지", "액션"),
                new MemberReportText.WritingSuggestion("자소서 문장", "포트폴리오 문장")
        ));

        mockMvc.perform(get("/api/dashboard/reports/20/members/7/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT004"))
                .andExpect(jsonPath("$.result.projectMemberId").value(7L))
                .andExpect(jsonPath("$.result.memberName").value("창훈"))
                .andExpect(jsonPath("$.result.finalScore").value(82.50))
                .andExpect(jsonPath("$.result.externalToolConnected").value(true))
                .andExpect(jsonPath("$.result.reliabilityTier").value("P2"))
                // 프로젝트 연동 여부와 멤버 점수 가용성은 별개다 — 점수가 없으면 null(응답에서는 생략)이다.
                .andExpect(jsonPath("$.result.externalScore").doesNotExist())
                .andExpect(jsonPath("$.result.totalTaskCount").value(13))
                .andExpect(jsonPath("$.result.completedTaskCount").value(12))
                .andExpect(jsonPath("$.result.deadlineMetTaskCount").value(11))
                // 개인 리포트 ②③④⑤ — 이스케이프된 문자열이 아니라 객체로 나가야 한다
                .andExpect(jsonPath("$.result.headline").value("적극적인 리더십으로 팀의 방향을 잡았어요"))
                .andExpect(jsonPath("$.result.strengths[0].title").value("주도성"))
                .andExpect(jsonPath("$.result.weakness.suggestions[0]").value("의견 제시를 늘려보세요"))
                .andExpect(jsonPath("$.result.growth.nextAction").value("액션"))
                .andExpect(jsonPath("$.result.writing.portfolio").value("포트폴리오 문장"));
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
