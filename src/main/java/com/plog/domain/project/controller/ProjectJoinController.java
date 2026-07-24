package com.plog.domain.project.controller;

import com.plog.domain.project.controller.docs.ProjectJoinControllerDoc;
import com.plog.domain.project.dto.request.ProjectJoinRequest;
import com.plog.domain.project.dto.response.ProjectJoinResponse;
import com.plog.domain.project.service.ProjectJoinService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectJoinController implements ProjectJoinControllerDoc {

    private final ProjectJoinService projectJoinService;

    @Override
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
