package com.plog.domain.integration.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.integration.service.NotionWebhookEventIngestionService;
import com.plog.domain.integration.service.NotionWebhookEventIngestionService.IngestionResult;
import com.plog.global.config.CorsProperties;
import com.plog.global.config.SecurityConfig;
import com.plog.global.security.jwt.JwtAccessDeniedHandler;
import com.plog.global.security.jwt.JwtAuthenticationEntryPoint;
import com.plog.global.security.jwt.JwtAuthenticationFilter;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaCookieAuthenticationFilter;
import com.plog.global.security.jwt.MediaTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotionWebhookEventController.class)
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        MediaCookieAuthenticationFilter.class
})
class NotionWebhookEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotionWebhookEventIngestionService ingestionService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MediaTokenProvider mediaTokenProvider;
    @MockitoBean
    private CorsProperties corsProperties;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void returnsAcceptedForNewVerifiedEvent() throws Exception {
        given(ingestionService.ingest(eq("{}"), eq("signature")))
                .willReturn(IngestionResult.EVENT_ACCEPTED);

        mockMvc.perform(post("/api/integrations/notion/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Notion-Signature", "signature")
                        .content("{}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void returnsOkForDuplicateIgnoredEvent() throws Exception {
        given(ingestionService.ingest(eq("{}"), eq("signature")))
                .willReturn(IngestionResult.DUPLICATE_IGNORED);

        mockMvc.perform(post("/api/integrations/notion/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Notion-Signature", "signature")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void returnsOkForVerificationChallenge() throws Exception {
        given(ingestionService.ingest(eq("{}"), eq("signature")))
                .willReturn(IngestionResult.VERIFICATION_ACCEPTED);

        mockMvc.perform(post("/api/integrations/notion/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Notion-Signature", "signature")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void returnsUnauthorizedForInvalidSignature() throws Exception {
        given(ingestionService.ingest(eq("{}"), eq("invalid")))
                .willReturn(IngestionResult.INVALID_SIGNATURE);

        mockMvc.perform(post("/api/integrations/notion/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Notion-Signature", "invalid")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsBadRequestForInvalidJsonContract() throws Exception {
        given(ingestionService.ingest(eq("invalid"), eq(null)))
                .willThrow(new IllegalArgumentException("invalid"));

        mockMvc.perform(post("/api/integrations/notion/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid"))
                .andExpect(status().isBadRequest());
    }
}
