package com.plog.domain.notification.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.notification.dto.NotificationSettingsDto;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.service.NotificationSettingsService;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaTokenProvider;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(NotificationSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationSettingsControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationSettingsService notificationSettingsService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private MediaTokenProvider mediaTokenProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(7L, null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 전체와_프로젝트별_설정을_조회한다() throws Exception {
        Map<NotificationType, Boolean> global = Map.of(
                NotificationType.CHAT_MESSAGE, false,
                NotificationType.CHAT_MENTION, true,
                NotificationType.NOTICE, true,
                NotificationType.PEER_EVALUATION_STARTED, true,
                NotificationType.REPORT_PUBLISHED, true);
        NotificationSettingsDto.ProjectSettings project = new NotificationSettingsDto.ProjectSettings(
                10L, "Plog", global);
        given(notificationSettingsService.get(7L))
                .willReturn(new NotificationSettingsDto.Response(global, List.of(project)));

        mockMvc.perform(get("/api/users/me/notification-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.global.CHAT_MESSAGE").value(false))
                .andExpect(jsonPath("$.result.projects[0].projectId").value(10L))
                .andExpect(jsonPath("$.result.projects[0].settings.NOTICE").value(true));
    }

    @Test
    void 전체_설정을_부분_PATCH한다() throws Exception {
        Map<NotificationType, Boolean> result = Map.of(NotificationType.CHAT_MESSAGE, false);
        given(notificationSettingsService.patchGlobal(7L, Map.of(NotificationType.CHAT_MESSAGE, false)))
                .willReturn(result);

        mockMvc.perform(patch("/api/users/me/notification-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"CHAT_MESSAGE\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.CHAT_MESSAGE").value(false));

        verify(notificationSettingsService).patchGlobal(7L, Map.of(NotificationType.CHAT_MESSAGE, false));
    }

    @Test
    void 프로젝트_설정을_부분_PATCH한다() throws Exception {
        NotificationSettingsDto.ProjectSettings result = new NotificationSettingsDto.ProjectSettings(
                10L, "Plog", Map.of(NotificationType.NOTICE, false));
        given(notificationSettingsService.patchProject(7L, 10L, Map.of(NotificationType.NOTICE, false)))
                .willReturn(result);

        mockMvc.perform(patch("/api/projects/10/notification-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"NOTICE\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.projectId").value(10L))
                .andExpect(jsonPath("$.result.settings.NOTICE").value(false));

        verify(notificationSettingsService).patchProject(7L, 10L, Map.of(NotificationType.NOTICE, false));
    }
}
