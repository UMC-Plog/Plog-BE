package com.plog.domain.project.controller;

import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.domain.project.dto.response.ProjectCreateResponse;
import com.plog.domain.project.service.ProjectCreateService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project", description = "프로젝트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectCreateService projectCreateService;

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
                    description = "잘못된 프로젝트 이름, 유형 또는 예상 종료일"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자"
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
}
