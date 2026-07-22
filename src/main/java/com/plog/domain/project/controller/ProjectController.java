package com.plog.domain.project.controller;

import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.domain.project.dto.ProjectStatusDto;
import com.plog.domain.project.dto.response.ProjectCreateResponse;
import com.plog.domain.project.service.ProjectCreateService;
import com.plog.domain.project.service.ProjectStatusService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
public class ProjectController {

    private final ProjectCreateService projectCreateService;
    private final ProjectStatusService projectStatusService;

    @Operation(
            summary = "프로젝트 생성",
            description = "프로젝트와 생성자 OWNER 멤버십을 생성하고 초대 정보를 발급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "프로젝트 생성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 프로젝트 이름, 유형 또는 예상 종료일",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
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
