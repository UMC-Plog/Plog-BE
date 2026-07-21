package com.plog.domain.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.project.dto.request.ProjectJoinRequest;
import com.plog.domain.project.dto.response.ProjectJoinResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.service.ProjectJoinService;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import java.time.Instant;
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

@WebMvcTest(ProjectJoinController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectJoinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectJoinService projectJoinService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void joinsAProjectWithTheRawLongPrincipal() throws Exception {
        authenticate(1L);
        given(projectJoinService.join(eq(1L), any(ProjectJoinRequest.class)))
                .willReturn(new ProjectJoinResponse(
                        10L,
                        "Plog API",
                        25L,
                        ProjectRole.MEMBER,
                        ProjectStatus.IN_PROGRESS,
                        MemberStatus.ACTIVE,
                        Instant.parse("2026-07-20T21:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/projects/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT005"))
                .andExpect(jsonPath("$.message").value("프로젝트에 참여했습니다."))
                .andExpect(jsonPath("$.result.projectId").value(10L))
                .andExpect(jsonPath("$.result.projectName").value("Plog API"))
                .andExpect(jsonPath("$.result.projectMemberId").value(25L))
                .andExpect(jsonPath("$.result.role").value("MEMBER"))
                .andExpect(jsonPath("$.result.projectStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.memberStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.result.joinedAt").value("2026-07-20T21:00:00Z"))
                .andExpect(jsonPath("$.result.status").doesNotExist());

        verify(projectJoinService).join(1L, new ProjectJoinRequest("valid-invite-code"));
    }

    @Test
    void rejectsABlankInviteCodeBeforeCallingTheService() throws Exception {
        authenticate(1L);

        mockMvc.perform(post("/api/v1/projects/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.inviteCode").value("초대 코드는 필수입니다."));

        verifyNoInteractions(projectJoinService);
    }

    @Test
    void returnsAnInvalidInviteCodeError() throws Exception {
        authenticate(1L);
        given(projectJoinService.join(eq(1L), any(ProjectJoinRequest.class)))
                .willThrow(new ApiException(ProjectErrorCode.INVALID_INVITE_CODE));

        mockMvc.perform(post("/api/v1/projects/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROJECT008"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 초대 코드입니다."));
    }

    @Test
    void returnsAnAlreadyJoinedConflict() throws Exception {
        authenticate(1L);
        given(projectJoinService.join(eq(1L), any(ProjectJoinRequest.class)))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_ALREADY_JOINED));

        mockMvc.perform(post("/api/v1/projects/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT009"))
                .andExpect(jsonPath("$.message").value("이미 참여 중인 프로젝트입니다."));
    }

    @Test
    void returnsInvalidTokenWhenTheAuthenticatedUserNoLongerExists() throws Exception {
        authenticate(404L);
        given(projectJoinService.join(eq(404L), any(ProjectJoinRequest.class)))
                .willThrow(new ApiException(AuthErrorCode.INVALID_TOKEN));

        mockMvc.perform(post("/api/v1/projects/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH011"));
    }

    @Test
    void passesANullPrincipalToTheServiceWhenSecurityFiltersAreDisabled() throws Exception {
        given(projectJoinService.join(isNull(), any(ProjectJoinRequest.class)))
                .willThrow(new ApiException(AuthErrorCode.INVALID_TOKEN));

        mockMvc.perform(post("/api/v1/projects/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH011"));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }

    private String validRequestJson() {
        return "{\"inviteCode\":\"valid-invite-code\"}";
    }
}
