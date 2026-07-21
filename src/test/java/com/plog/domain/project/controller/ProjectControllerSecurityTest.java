package com.plog.domain.project.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.project.service.ProjectCreateService;
import com.plog.domain.project.service.ProjectStatusService;
import com.plog.global.config.CorsProperties;
import com.plog.global.config.SecurityConfig;
import com.plog.global.security.jwt.JwtAccessDeniedHandler;
import com.plog.global.security.jwt.JwtAuthenticationEntryPoint;
import com.plog.global.security.jwt.JwtAuthenticationFilter;
import com.plog.global.security.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        ProjectControllerSecurityTest.TestCorsConfiguration.class
})
class ProjectControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCreateService projectCreateService;

    @MockitoBean
    private ProjectStatusService projectStatusService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void rejectsARequestWithoutAuthenticationBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        verifyNoInteractions(projectCreateService);
    }

    @Test
    void rejectsAnInvalidBearerTokenBeforeCallingTheService() throws Exception {
        given(jwtProvider.parseUserId("invalid-access-token"))
                .willThrow(new JwtException("invalid token"));

        mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH011"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));

        verifyNoInteractions(projectCreateService);
    }

    private String validRequestJson() {
        return """
                {
                  "projectName": "Plog API",
                  "projectType": "DEVELOP",
                  "endDay": "2099-12-31"
                }
                """;
    }

    @TestConfiguration
    static class TestCorsConfiguration {

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties(List.of("http://localhost:3000"));
        }
    }
}
