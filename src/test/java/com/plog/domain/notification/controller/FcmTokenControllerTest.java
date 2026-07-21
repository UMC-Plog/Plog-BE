package com.plog.domain.notification.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.notification.dto.FcmTokenDto;
import com.plog.domain.notification.service.FcmTokenService;
import com.plog.global.security.jwt.JwtProvider;
import java.time.Instant;
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

@WebMvcTest(FcmTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
class FcmTokenControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FcmTokenService fcmTokenService;
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
    void putUsesUnversionedPathAndAuthenticatedUserId() throws Exception {
        given(fcmTokenService.put(eq(7L), eq(new FcmTokenDto.Request("device-token"))))
                .willReturn(new FcmTokenDto.Response(
                        3L, 7L, "device-token", Instant.parse("2026-07-21T01:00:00Z")));

        mockMvc.perform(put("/users/me/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"device-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.fcmId").value(3L))
                .andExpect(jsonPath("$.result.userId").value(7L));

        verify(fcmTokenService).put(eq(7L), eq(new FcmTokenDto.Request("device-token")));
    }

    @Test
    void deleteIsIdempotentAndLegacyPostIsNotExposed() throws Exception {
        given(fcmTokenService.delete(eq(7L), eq(new FcmTokenDto.Request("device-token"))))
                .willReturn(new FcmTokenDto.DeletedResponse(true));

        mockMvc.perform(delete("/users/me/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"device-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.deleted").value(true));

        mockMvc.perform(post("/users/me/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"device-token\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void invalidTokenReturnsContractValidationCode() throws Exception {
        mockMvc.perform(put("/users/me/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
