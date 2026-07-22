package com.plog.domain.evaluation.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.evaluation.dto.request.PeerEvaluationCreateRequest;
import com.plog.domain.evaluation.dto.response.PeerEvaluationCreateResponse;
import com.plog.domain.evaluation.service.EvaluationService;
import com.plog.global.security.jwt.JwtProvider;
import java.util.List;
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

@WebMvcTest(EvaluationController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationService evaluationService;

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
    void updatesPeerEvaluationAtUnversionedPath() throws Exception {
        PeerEvaluationCreateRequest request = new PeerEvaluationCreateRequest(
                4, 4, 5, 5, 4, List.of("소통능력"), "수정된 동료 평가");
        given(evaluationService.updatePeerEvaluation(1L, 10L, 7L, request))
                .willReturn(new PeerEvaluationCreateResponse(105L, false));

        mockMvc.perform(put("/api/projects/{projectId}/evaluations/peers/{targetMemberId}", 1L, 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "collaborationScore": 4,
                                  "initiativeScore": 4,
                                  "responsibilityScore": 5,
                                  "communicationScore": 5,
                                  "outputScore": 4,
                                  "keywords": ["소통능력"],
                                  "feedback": "수정된 동료 평가"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EVAL200_4"))
                .andExpect(jsonPath("$.result.peerId").value(105L))
                .andExpect(jsonPath("$.result.isNudgeTriggered").value(false));

        verify(evaluationService).updatePeerEvaluation(1L, 10L, 7L, request);
    }
}
