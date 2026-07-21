package com.plog.domain.project.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.project.dto.response.ProjectListResponse;
import com.plog.domain.project.dto.response.ProjectListResponse.MemberPreview;
import com.plog.domain.project.dto.response.ProjectListResponse.ProjectSummary;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.service.ProjectListService;
import com.plog.global.security.jwt.JwtProvider;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectListController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectListService projectListService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheProjectListForTheRawLongPrincipal() throws Exception {
        authenticate(1L);
        ProjectListResponse response = new ProjectListResponse(
                List.of(new ProjectSummary(
                        10L,
                        "Plog",
                        ProjectType.DEVELOP,
                        ProjectStatus.IN_PROGRESS,
                        LocalDate.of(2026, 8, 31),
                        42,
                        1,
                        List.of(new MemberPreview(1L, "vana", null)),
                        0,
                        66
                )),
                0,
                20,
                1,
                1,
                true,
                true
        );
        given(projectListService.getProjects(1L, ProjectStatus.IN_PROGRESS, 0, 20))
                .willReturn(response);

        mockMvc.perform(get("/api/v1/projects")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT007"))
                .andExpect(jsonPath("$.result.content[0].projectId").value(10L))
                .andExpect(jsonPath("$.result.content[0].projectType").value("DEVELOP"))
                .andExpect(jsonPath("$.result.content[0].memberPreviews[0].nickname").value("vana"))
                .andExpect(jsonPath("$.result.content[0].progressPercent").value(66))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    void rejectsAnInvalidPageBeforeCallingTheService() throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/v1/projects").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(projectListService);
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }
}
