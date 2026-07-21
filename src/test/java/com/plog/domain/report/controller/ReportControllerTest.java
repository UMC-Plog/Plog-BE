package com.plog.domain.report.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.report.dto.response.ReportSearchResponse;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.service.ReportSearchService;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.SliceResponse;
import com.plog.global.security.jwt.JwtProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchesReportsForTheRawLongPrincipalWithDefaultPaging() throws Exception {
        authenticate(1L);
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 20, 12, 0);
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

        mockMvc.perform(get("/api/v1/dashboard/reports/search")
                        .param("keyword", "plog")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORT001"))
                .andExpect(jsonPath("$.result.content[0].projectId").value(10L))
                .andExpect(jsonPath("$.result.content[0].projectName").value("Plog"))
                .andExpect(jsonPath("$.result.content[0].reportId").value(20L))
                .andExpect(jsonPath("$.result.content[0].reportStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.result.content[0].completedAt")
                        .value("2026-07-20T12:00:00"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.totalElements").doesNotExist());
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

        mockMvc.perform(get("/api/v1/dashboard/reports/search")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT002"));
    }

    @Test
    void rejectsAnInvalidPageBeforeCallingTheService() throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/v1/dashboard/reports/search").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    void rejectsAnInvalidSizeBeforeCallingTheService(String size) throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/v1/dashboard/reports/search").param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAMalformedDateBeforeCallingTheService() throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/v1/dashboard/reports/search")
                        .param("startDate", "2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(service);
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }
}
