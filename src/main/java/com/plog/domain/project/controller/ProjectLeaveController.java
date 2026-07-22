package com.plog.domain.project.controller;

import com.plog.domain.project.dto.response.ProjectLeaveResponse;
import com.plog.domain.project.service.ProjectLeaveService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectLeaveController {

    private final ProjectLeaveService projectLeaveService;

    @Operation(summary = "프로젝트 나가기", description = "프로젝트에서 나가며, 마지막 활성 멤버가 나가면 프로젝트를 영구 삭제합니다.")
    @DeleteMapping("/{projectId}/members/me")
    public ResponseEntity<ApiResponse<ProjectLeaveResponse>> leave(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        ProjectLeaveResponse response = projectLeaveService.leave(projectId, userId);

        return ResponseEntity
                .status(ProjectSuccessCode.PROJECT_LEFT_SUCCESS.getHttpStatus())
                .body(ApiResponse.success(ProjectSuccessCode.PROJECT_LEFT_SUCCESS, response));
    }
}
