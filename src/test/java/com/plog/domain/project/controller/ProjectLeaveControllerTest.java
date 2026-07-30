package com.plog.domain.project.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.project.dto.response.ProjectLeaveResponse;
import com.plog.domain.project.service.ProjectLeaveService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectLeaveController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectLeaveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectLeaveService projectLeaveService;

    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MediaTokenProvider mediaTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(7L, null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leavesProjectAtUnversionedPath() throws Exception {
        given(projectLeaveService.leave(1L, 7L)).willReturn(new ProjectLeaveResponse(true));

        mockMvc.perform(delete("/api/projects/{projectId}/members/me", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJ200_4"))
                .andExpect(jsonPath("$.result.success").value(true));

        verify(projectLeaveService).leave(1L, 7L);
    }

    @Test
    void returnsOwnerMustTransferBusinessError() throws Exception {
        given(projectLeaveService.leave(1L, 7L))
                .willThrow(new ApiException(ProjectErrorCode.OWNER_MUST_TRANSFER));

        mockMvc.perform(delete("/api/projects/{projectId}/members/me", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("OWNER_MUST_TRANSFER"))
                .andExpect(jsonPath("$.message")
                        .value("프로젝트 생성자는 다른 팀원에게 방장 권한을 이전해야 나갈 수 있습니다."));
    }
}
