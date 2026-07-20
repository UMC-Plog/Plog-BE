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

import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.domain.project.dto.response.ProjectCreateResponse;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.service.ProjectCreateService;
import com.plog.global.api.error.AuthErrorCode;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCreateService projectCreateService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsAProjectWithTheRawLongPrincipal() throws Exception {
        Long userId = 1L;
        LocalDate startDay = LocalDate.of(2026, 7, 20);
        LocalDate endDay = LocalDate.of(2026, 8, 31);
        authenticate(userId);
        given(projectCreateService.create(eq(userId), any(ProjectCreateRequest.class)))
                .willReturn(new ProjectCreateResponse(
                        10L,
                        "Plog API",
                        ProjectType.DEVELOP,
                        ProjectStatus.IN_PROGRESS,
                        startDay,
                        endDay,
                        20L,
                        ProjectRole.OWNER,
                        new ProjectCreateResponse.Invite(
                                "invite-code",
                                "https://plog.test/invites/invite-code"
                        )
                ));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectName": " Plog API ",
                                  "projectType": "DEVELOP",
                                  "endDay": "2026-08-31"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT004"))
                .andExpect(jsonPath("$.message").value("프로젝트를 생성했습니다."))
                .andExpect(jsonPath("$.result.projectId").value(10L))
                .andExpect(jsonPath("$.result.projectName").value("Plog API"))
                .andExpect(jsonPath("$.result.projectType").value("DEVELOP"))
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.startDay").value("2026-07-20"))
                .andExpect(jsonPath("$.result.endDay").value("2026-08-31"))
                .andExpect(jsonPath("$.result.myProjectMemberId").value(20L))
                .andExpect(jsonPath("$.result.myRole").value("OWNER"))
                .andExpect(jsonPath("$.result.invite.inviteCode").value("invite-code"))
                .andExpect(jsonPath("$.result.invite.inviteUrl")
                        .value("https://plog.test/invites/invite-code"));

        verify(projectCreateService).create(
                userId,
                new ProjectCreateRequest(" Plog API ", ProjectType.DEVELOP, endDay)
        );
    }

    @Test
    void returnsTheProjectNameValidationError() throws Exception {
        authenticate(1L);
        given(projectCreateService.create(eq(1L), any(ProjectCreateRequest.class)))
                .willThrow(new ApiException(ProjectErrorCode.INVALID_PROJECT_NAME));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("P")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT004"))
                .andExpect(jsonPath("$.message").value("프로젝트명은 2자 이상 20자 이하여야 합니다."));
    }

    @Test
    void returnsTheProjectEndDayValidationError() throws Exception {
        authenticate(1L);
        given(projectCreateService.create(eq(1L), any(ProjectCreateRequest.class)))
                .willThrow(new ApiException(ProjectErrorCode.INVALID_PROJECT_END_DAY));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("Plog API")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT005"))
                .andExpect(jsonPath("$.message").value("예상 종료일은 오늘과 시작일 이후여야 합니다."));
    }

    @Test
    void returnsInvalidTokenWhenTheAuthenticatedUserNoLongerExists() throws Exception {
        authenticate(404L);
        given(projectCreateService.create(eq(404L), any(ProjectCreateRequest.class)))
                .willThrow(new ApiException(AuthErrorCode.INVALID_TOKEN));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("Plog API")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH011"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));
    }

    @Test
    void rejectsAMissingProjectTypeBeforeCallingTheService() throws Exception {
        authenticate(1L);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectName": "Plog API",
                                  "endDay": "2026-08-31"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.projectType").value("프로젝트 유형은 필수입니다."));

        verifyNoInteractions(projectCreateService);
    }

    @Test
    void rejectsAnUnknownProjectTypeBeforeCallingTheService() throws Exception {
        authenticate(1L);

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectName": "Plog API",
                                  "projectType": "TEAM_PROJECT",
                                  "endDay": "2026-08-31"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.message").value("요청을 읽을 수 없습니다."));

        verifyNoInteractions(projectCreateService);
    }

    @Test
    void passesANullPrincipalToTheServiceWhenSecurityFiltersAreDisabled() throws Exception {
        given(projectCreateService.create(isNull(), any(ProjectCreateRequest.class)))
                .willThrow(new ApiException(AuthErrorCode.INVALID_TOKEN));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("Plog API")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH011"));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }

    private String validRequestJson(String projectName) {
        return """
                {
                  "projectName": "%s",
                  "projectType": "DEVELOP",
                  "endDay": "2026-08-31"
                }
                """.formatted(projectName);
    }
}
