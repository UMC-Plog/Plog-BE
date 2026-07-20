package com.plog.domain.project.controller;

import com.plog.domain.project.dto.request.ProjectJoinRequest;
import com.plog.domain.project.dto.response.ProjectJoinResponse;
import com.plog.domain.project.service.ProjectJoinService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
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
public class ProjectJoinController {

    private final ProjectJoinService projectJoinService;

    @Operation(
            summary = "초대 코드로 프로젝트 참여",
            description = "유효한 초대 코드로 프로젝트에 MEMBER/ACTIVE 상태로 참여합니다."
    )
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<ProjectJoinResponse>> joinProject(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProjectJoinRequest request
    ) {
        ProjectJoinResponse response = projectJoinService.join(userId, request);
        return ResponseEntity
                .status(ProjectSuccessCode.PROJECT_JOINED.getHttpStatus())
                .body(ApiResponse.success(ProjectSuccessCode.PROJECT_JOINED, response));
    }
}
