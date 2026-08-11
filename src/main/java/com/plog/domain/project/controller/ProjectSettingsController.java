package com.plog.domain.project.controller;

import com.plog.domain.project.dto.ProjectSettingsDto;
import com.plog.domain.project.service.ProjectSettingsService;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/projects/{projectId}/settings")
@RequiredArgsConstructor
@Tag(name = "Project", description = "프로젝트, 멤버 및 설정 관리 API")
public class ProjectSettingsController {
    private final ProjectSettingsService projectSettingsService;

    @GetMapping
    public ApiResponse<ProjectSettingsDto.Response> getSettings(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.success(projectSettingsService.getSettings(projectId, userId));
    }

    @PatchMapping
    public ApiResponse<ProjectSettingsDto.UpdateResponse> updateSettings(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProjectSettingsDto.UpdateRequest request
    ) {
        return ApiResponse.success(projectSettingsService.updateSettings(projectId, userId, request));
    }
}
