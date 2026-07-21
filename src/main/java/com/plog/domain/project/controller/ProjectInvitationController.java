package com.plog.domain.project.controller;

import com.plog.domain.project.controller.docs.ProjectInvitationControllerDoc;
import com.plog.domain.project.dto.response.ProjectInvitationPreviewResponse;
import com.plog.domain.project.service.ProjectInvitationPreviewService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
public class ProjectInvitationController implements ProjectInvitationControllerDoc {

    private final ProjectInvitationPreviewService previewService;

    @Override
    @GetMapping("/invitations/{inviteCode}")
    public ResponseEntity<ApiResponse<ProjectInvitationPreviewResponse>> getInvitationPreview(
            @AuthenticationPrincipal Long userId,
            @PathVariable String inviteCode
    ) {
        ProjectInvitationPreviewResponse response = previewService.preview(userId, inviteCode);
        return ResponseEntity.ok(ApiResponse.success(
                ProjectSuccessCode.PROJECT_INVITATION_PREVIEW_RETRIEVED,
                response
        ));
    }
}
