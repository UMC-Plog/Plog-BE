package com.plog.domain.project.controller;

import com.plog.domain.project.controller.docs.ProjectRoleControllerDoc;
import com.plog.domain.project.dto.request.ProjectRoleDelegationRequest;
import com.plog.domain.project.dto.response.ProjectRoleDelegationResponse;
import com.plog.domain.project.service.ProjectRoleDelegationService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectRoleController implements ProjectRoleControllerDoc {

    private final ProjectRoleDelegationService projectRoleDelegationService;

    @Override
    @PatchMapping("/{projectId}/members/{targetMemberId}/role")
    public ResponseEntity<ApiResponse<ProjectRoleDelegationResponse>> delegateRole(
            @PathVariable Long projectId,
            @PathVariable Long targetMemberId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProjectRoleDelegationRequest request
    ) {
        ProjectRoleDelegationResponse response = projectRoleDelegationService.delegateRole(
                projectId,
                userId,
                targetMemberId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(
                ProjectSuccessCode.PROJECT_ROLE_DELEGATED,
                response
        ));
    }
}
