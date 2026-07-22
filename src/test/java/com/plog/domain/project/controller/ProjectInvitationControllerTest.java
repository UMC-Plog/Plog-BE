package com.plog.domain.project.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.project.dto.response.ProjectInvitationPreviewResponse;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.service.ProjectInvitationPreviewService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import java.time.LocalDate;
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

@WebMvcTest(ProjectInvitationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectInvitationPreviewService previewService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheProjectInvitationPreview() throws Exception {
        authenticate(1L);
        given(previewService.preview(1L, "valid-code"))
                .willReturn(new ProjectInvitationPreviewResponse(
                        10L,
                        "Plog API",
                        ProjectType.DEVELOP,
                        LocalDate.of(2026, 8, 20)
                ));

        mockMvc.perform(get("/api/projects/invitations/{inviteCode}", "valid-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT008"))
                .andExpect(jsonPath("$.message").value("프로젝트 초대 정보를 조회했습니다."))
                .andExpect(jsonPath("$.result.projectId").value(10L))
                .andExpect(jsonPath("$.result.projectName").value("Plog API"))
                .andExpect(jsonPath("$.result.projectType").value("DEVELOP"))
                .andExpect(jsonPath("$.result.endDay").value("2026-08-20"));
    }

    @Test
    void returnsAnInvalidInviteCodeError() throws Exception {
        authenticate(1L);
        given(previewService.preview(1L, "invalid-code"))
                .willThrow(new ApiException(ProjectErrorCode.INVALID_INVITE_CODE));

        mockMvc.perform(get("/api/projects/invitations/{inviteCode}", "invalid-code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT008"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 초대 코드입니다."));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }
}
