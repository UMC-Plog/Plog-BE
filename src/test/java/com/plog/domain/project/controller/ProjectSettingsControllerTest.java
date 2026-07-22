package com.plog.domain.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.project.dto.ProjectSettingsDto;
import com.plog.domain.project.service.ProjectSettingsService;
import com.plog.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(ProjectSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectSettingsControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProjectSettingsService projectSettingsService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(7L, null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesSettingsEndpointsWithoutApiVersionPrefix() throws Exception {
        mockMvc.perform(get("/projects/1/settings"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/projects/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectName\":\"Plog renewal\",\"projectType\":\"DEVELOP\"}"))
                .andExpect(status().isOk());

        verify(projectSettingsService).getSettings(1L, 7L);
        verify(projectSettingsService).updateSettings(
                eq(1L), eq(7L), any(ProjectSettingsDto.UpdateRequest.class));
    }

    @Test
    void malformedProjectTypeUsesValidationContract() throws Exception {
        mockMvc.perform(patch("/projects/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectType\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
