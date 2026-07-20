package com.plog.domain.project.controller;

import com.plog.domain.project.dto.ProjectSettingsDto;
import com.plog.domain.project.service.ProjectSettingsService;
import com.plog.global.api.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/settings")
@RequiredArgsConstructor
public class ProjectSettingsController {
    private final ProjectSettingsService projectSettingsService;

    @GetMapping
    public ApiResponse<ProjectSettingsDto.Response> getSettings(
            @PathVariable Long projectId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return ApiResponse.success(projectSettingsService.getSettings(projectId, userId));
    }

    @PatchMapping
    public ApiResponse<ProjectSettingsDto.UpdateResponse> updateSettings(
            @PathVariable Long projectId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody ProjectSettingsDto.UpdateRequest request
    ) {
        return ApiResponse.success(projectSettingsService.updateSettings(projectId, userId, request));
    }
}
