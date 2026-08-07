package com.plog.domain.notification.controller;

import com.plog.domain.notification.dto.NotificationSettingsDto;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.service.NotificationSettingsService;
import com.plog.global.api.response.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class NotificationSettingsController {
    private final NotificationSettingsService notificationSettingsService;

    @GetMapping("/users/me/notification-settings")
    public ApiResponse<NotificationSettingsDto.Response> get(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(notificationSettingsService.get(userId));
    }

    @PatchMapping("/users/me/notification-settings")
    public ApiResponse<Map<NotificationType, Boolean>> patchGlobal(
            @AuthenticationPrincipal Long userId,
            @RequestBody Map<NotificationType, Boolean> request) {
        return ApiResponse.success(notificationSettingsService.patchGlobal(userId, request));
    }

    @PatchMapping("/projects/{projectId}/notification-settings")
    public ApiResponse<NotificationSettingsDto.ProjectSettings> patchProject(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @RequestBody Map<NotificationType, Boolean> request) {
        return ApiResponse.success(notificationSettingsService.patchProject(userId, projectId, request));
    }
}
