package com.plog.domain.project.controller;

import com.plog.domain.project.controller.docs.ProjectInviteControllerDoc;
import com.plog.domain.project.dto.response.ProjectInviteReissueResponse;
import com.plog.domain.project.service.ProjectInviteService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectInviteController implements ProjectInviteControllerDoc {

    private final ProjectInviteService projectInviteService;

    @Override
    @PostMapping("/{projectId}/invite")
    public ResponseEntity<ApiResponse<ProjectInviteReissueResponse>> reissueInvite(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        ProjectInviteReissueResponse response = projectInviteService.reissue(projectId, userId);
        return ResponseEntity.ok(ApiResponse.success(
                ProjectSuccessCode.PROJECT_INVITE_REISSUED,
                response
        ));
    }
}
