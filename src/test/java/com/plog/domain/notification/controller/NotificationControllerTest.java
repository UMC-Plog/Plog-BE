package com.plog.domain.notification.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.notification.dto.response.NotificationResponse;
import com.plog.domain.notification.exception.NotificationErrorCode;
import com.plog.domain.notification.service.NotificationCommandService;
import com.plog.domain.notification.service.NotificationQueryService;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.SliceResponse;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaTokenProvider;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationQueryService notificationQueryService;
    @MockitoBean private NotificationCommandService notificationCommandService;
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
    void 음수_페이지는_잘못된_입력으로_거부한다() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .queryParam("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(notificationQueryService);
    }

    @Test
    void 정상_페이지와_크기로_알림_목록을_조회한다() throws Exception {
        given(notificationQueryService.getNotifications(7L, 2, 30))
                .willReturn(new SliceResponse<NotificationResponse>(List.of(), 2, 30, false));

        mockMvc.perform(get("/api/notifications")
                        .queryParam("page", "2")
                        .queryParam("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(2))
                .andExpect(jsonPath("$.result.size").value(30));

        verify(notificationQueryService).getNotifications(7L, 2, 30);
    }

    @Test
    void 최대_크기를_초과하면_잘못된_입력으로_거부한다() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(notificationQueryService);
    }

    @Test
    void 본인_알림을_읽음_처리한다() throws Exception {
        mockMvc.perform(patch("/api/notifications/10/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(notificationCommandService).markAsRead(7L, 10L);
    }

    @Test
    void 로그인_사용자의_알림을_전체_읽음_처리한다() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(notificationCommandService).markAllAsRead(7L);
    }

    @Test
    void 다른_사용자의_알림은_찾을_수_없는_알림으로_응답한다() throws Exception {
        doThrow(new ApiException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
                .when(notificationCommandService).markAsRead(7L, 10L);

        mockMvc.perform(patch("/api/notifications/10/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }
}
