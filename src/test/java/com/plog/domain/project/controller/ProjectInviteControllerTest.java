package com.plog.domain.project.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.project.dto.response.ProjectInviteReissueResponse;
import com.plog.domain.project.service.ProjectInviteService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
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

@WebMvcTest(ProjectInviteController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectInviteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectInviteService projectInviteService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reissuesAnInviteWithTheRawLongPrincipal() throws Exception {
        authenticate(1L);
        given(projectInviteService.reissue(10L, 1L)).willReturn(new ProjectInviteReissueResponse(
                "new-code",
                "https://plog.test/invite/new-code",
                true
        ));

        mockMvc.perform(post("/api/v1/projects/{projectId}/invite", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT006"))
                .andExpect(jsonPath("$.result.inviteCode").value("new-code"))
                .andExpect(jsonPath("$.result.inviteUrl")
                        .value("https://plog.test/invite/new-code"))
                .andExpect(jsonPath("$.result.previousInviteInvalidated").value(true));

        verify(projectInviteService).reissue(10L, 1L);
    }

    @Test
    void returnsForbiddenWhenTheUserIsNotTheOwner() throws Exception {
        authenticate(2L);
        given(projectInviteService.reissue(10L, 2L))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED));

        mockMvc.perform(post("/api/v1/projects/{projectId}/invite", 10L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT003"));
    }

    @Test
    void returnsNotFoundForAnUnknownProject() throws Exception {
        authenticate(1L);
        given(projectInviteService.reissue(404L, 1L))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(post("/api/v1/projects/{projectId}/invite", 404L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT001"));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }
}
