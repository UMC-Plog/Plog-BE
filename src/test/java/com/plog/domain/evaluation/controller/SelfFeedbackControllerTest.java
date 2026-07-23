package com.plog.domain.evaluation.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.evaluation.dto.request.SelfFeedbackCreateRequest;
import com.plog.domain.evaluation.dto.response.SelfFeedbackUpdateResponse;
import com.plog.domain.evaluation.service.SelfFeedbackService;
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

@WebMvcTest(SelfFeedbackController.class)
@AutoConfigureMockMvc(addFilters = false)
class SelfFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SelfFeedbackService selfFeedbackService;

    @MockitoBean
    private JwtProvider jwtProvider;

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
    void updatesSelfFeedbackAtUnversionedPath() throws Exception {
        given(selfFeedbackService.updateSelfFeedback(1L, 7L, new SelfFeedbackCreateRequest("수정된 피드백")))
                .willReturn(new SelfFeedbackUpdateResponse(12L));

        mockMvc.perform(put("/api/projects/{projectId}/self-feedbacks", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정된 피드백\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EVAL200_6"))
                .andExpect(jsonPath("$.result.selfFeedbackId").value(12L));

        verify(selfFeedbackService).updateSelfFeedback(1L, 7L, new SelfFeedbackCreateRequest("수정된 피드백"));
    }

    @Test
    void rejectsBlankContent() throws Exception {
        mockMvc.perform(put("/api/projects/{projectId}/self-feedbacks", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }
}
