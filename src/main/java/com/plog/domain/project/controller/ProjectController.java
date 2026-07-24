package com.plog.domain.project.controller;

import com.plog.domain.project.controller.docs.ProjectControllerDoc;
import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.dto.response.ProjectCreateResponse;
import com.plog.domain.project.service.ProjectCreateService;
import com.plog.domain.project.service.ProjectStatusService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project", description = "프로젝트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectController implements ProjectControllerDoc {

    private final ProjectCreateService projectCreateService;
    private final ProjectStatusService projectStatusService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectCreateResponse>> createProject(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProjectCreateRequest request
    ) {
        ProjectCreateResponse response = projectCreateService.create(userId, request);

        return ResponseEntity
                .status(ProjectSuccessCode.PROJECT_CREATED.getHttpStatus())
                .body(ApiResponse.success(ProjectSuccessCode.PROJECT_CREATED, response));
    }

    @Operation(
            summary = "프로젝트 상태 전환 및 타임아웃 검증",
            description = "전원 평가 제출 또는 종료일 7일 경과 여부를 확인해 프로젝트 완료 상태로 전환합니다."
    )
    @PatchMapping("/{projectId}/status")
    public ResponseEntity<ApiResponse<ProjectStatusDto.Response>> checkAndUpdateStatus(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) ProjectStatusDto.Request request
    ) {
        ProjectStatusDto.Response response = projectStatusService.checkAndUpdateStatus(projectId, userId, request);

        return ResponseEntity
                .status(ProjectSuccessCode.PROJECT_STATUS_UPDATED.getHttpStatus())
                .body(ApiResponse.success(ProjectSuccessCode.PROJECT_STATUS_UPDATED, response));
    }
}
