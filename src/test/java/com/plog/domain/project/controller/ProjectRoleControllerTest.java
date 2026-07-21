package com.plog.domain.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.project.dto.request.ProjectRoleDelegationRequest;
import com.plog.domain.project.dto.response.ProjectRoleDelegationResponse;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.service.ProjectRoleDelegationService;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectRoleController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectRoleDelegationService projectRoleDelegationService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatesProjectRoleWithTheRawLongPrincipal() throws Exception {
        authenticate(7L);
        givenResponse();

        mockMvc.perform(patch("/api/v1/projects/1/members/20/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJ200_3"))
                .andExpect(jsonPath("$.message").value("프로젝트 방장 권한 위임 성공"))
                .andExpect(jsonPath("$.result.projectId").value(1L))
                .andExpect(jsonPath("$.result.newOwnerMemberId").value(20L));

        verify(projectRoleDelegationService).delegateRole(
                eq(1L),
                eq(7L),
                eq(20L),
                any(ProjectRoleDelegationRequest.class)
        );
    }

    @Test
    void returnsTheProjectPermissionError() throws Exception {
        authenticate(7L);
        org.mockito.BDDMockito.given(projectRoleDelegationService.delegateRole(
                        eq(1L),
                        eq(7L),
                        eq(20L),
                        any(ProjectRoleDelegationRequest.class)))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED));

        mockMvc.perform(patch("/api/v1/projects/1/members/20/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT003"));
    }

    @Test
    void rejectsAMissingRoleBeforeCallingTheService() throws Exception {
        authenticate(7L);

        mockMvc.perform(patch("/api/v1/projects/1/members/20/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(projectRoleDelegationService);
    }

    @Test
    void returnsInvalidTokenWhenTheAuthenticatedUserNoLongerExists() throws Exception {
        authenticate(404L);
        org.mockito.BDDMockito.given(projectRoleDelegationService.delegateRole(
                        eq(1L),
                        eq(404L),
                        eq(20L),
                        any(ProjectRoleDelegationRequest.class)))
                .willThrow(new ApiException(AuthErrorCode.INVALID_TOKEN));

        mockMvc.perform(patch("/api/v1/projects/1/members/20/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH011"));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }

    private void givenResponse() {
        org.mockito.BDDMockito.given(projectRoleDelegationService.delegateRole(
                        eq(1L),
                        eq(7L),
                        eq(20L),
                        any(ProjectRoleDelegationRequest.class)))
                .willReturn(new ProjectRoleDelegationResponse(1L, 20L));
    }
}
