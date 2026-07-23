package com.plog.domain.project.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.project.service.ProjectJoinService;
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

@WebMvcTest(ProjectJoinController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        ProjectJoinControllerSecurityTest.TestCorsConfiguration.class
})
class ProjectJoinControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectJoinService projectJoinService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void rejectsARequestWithoutAuthenticationBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/projects/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        verifyNoInteractions(projectJoinService);
    }

    @Test
    void rejectsAnInvalidBearerTokenBeforeCallingTheService() throws Exception {
        given(jwtProvider.parseUserId("invalid-access-token"))
                .willThrow(new JwtException("invalid token"));

        mockMvc.perform(post("/api/projects/join")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH011"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));

        verifyNoInteractions(projectJoinService);
    }

    private String validRequestJson() {
        return "{\"inviteCode\":\"valid-invite-code\"}";
    }

    @TestConfiguration
    static class TestCorsConfiguration {

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties(List.of("http://localhost:3000"));
        }
    }
}
