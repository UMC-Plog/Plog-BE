package com.plog.domain.integration.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.integration.dto.response.ExternalLinkItemResponse;
import com.plog.domain.integration.dto.response.ExternalLinkStatusResponse;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.service.ExternalLinkService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExternalLinkController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExternalLinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExternalLinkService externalLinkService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내 외부 툴 연동 상태를 공통 ApiResponse 형식으로 조회하고 accessToken은 노출하지 않는다")
    void getMyExternalLinksReturnsApiResponse() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(externalLinkService.getMyExternalLinks(eq(projectId), eq(userId)))
                .willReturn(new ExternalLinkStatusResponse(projectId, 100L, List.of(
                        new ExternalLinkItemResponse(LinkType.GITHUB, true, "github-user"),
                        new ExternalLinkItemResponse(LinkType.FIGMA, false, null),
                        new ExternalLinkItemResponse(LinkType.NOTION, true, "notion-user")
                )));

        mockMvc.perform(get("/api/projects/{projectId}/me/external-links", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT001"))
                .andExpect(jsonPath("$.message").value("외부 툴 연동 상태를 조회했습니다."))
                .andExpect(jsonPath("$.result.projectId").value(projectId))
                .andExpect(jsonPath("$.result.projectMemberId").value(100L))
                .andExpect(jsonPath("$.result.links[0].linkType").value("GITHUB"))
                .andExpect(jsonPath("$.result.links[0].linked").value(true))
                .andExpect(jsonPath("$.result.links[0].connectedAccountName").value("github-user"))
                .andExpect(jsonPath("$.result.links[1].linkType").value("FIGMA"))
                .andExpect(jsonPath("$.result.links[1].linked").value(false))
                .andExpect(jsonPath("$.result.links[1].connectedAccountName").value(nullValue()))
                .andExpect(jsonPath("$.result.links[2].linkType").value("NOTION"))
                .andExpect(jsonPath("$.result.links[2].linked").value(true))
                .andExpect(jsonPath("$.result.links[2].connectedAccountName").value("notion-user"))
                .andExpect(jsonPath("$.result.links[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.result.links[1].accessToken").doesNotExist())
                .andExpect(jsonPath("$.result.links[2].accessToken").doesNotExist());
    }

    @Test
    @DisplayName("프로젝트가 없으면 404와 PROJECT001을 반환한다")
    void getMyExternalLinksReturnsNotFound() throws Exception {
        Long projectId = 404L;
        Long userId = 10L;
        authenticate(userId);
        given(externalLinkService.getMyExternalLinks(eq(projectId), eq(userId)))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(get("/api/projects/{projectId}/me/external-links", projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT001"))
                .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("활성 프로젝트 멤버가 아니면 403과 PROJECT002를 반환한다")
    void getMyExternalLinksReturnsForbidden() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(externalLinkService.getMyExternalLinks(eq(projectId), eq(userId)))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        mockMvc.perform(get("/api/projects/{projectId}/me/external-links", projectId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT002"))
                .andExpect(jsonPath("$.message").value("활성 프로젝트 멤버만 접근할 수 있습니다."));
    }

    @Test
    @DisplayName("projectId 타입이 올바르지 않으면 400과 COMMON400을 반환한다")
    void getMyExternalLinksRejectsMalformedProjectId() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/api/projects/{projectId}/me/external-links", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));

        verifyNoInteractions(externalLinkService);
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }
}
