package com.plog.domain.project.controller;

import com.plog.domain.project.controller.docs.ProjectMemberControllerDoc;
import com.plog.domain.project.dto.response.ActiveProjectMemberResponse;
import com.plog.domain.project.service.ProjectMemberListService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectMemberController implements ProjectMemberControllerDoc {

    private final ProjectMemberListService projectMemberListService;

    @Override
    @GetMapping("/{projectId}/members")
    public ResponseEntity<ApiResponse<List<ActiveProjectMemberResponse>>> getActiveMembers(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        List<ActiveProjectMemberResponse> response =
                projectMemberListService.getActiveMembers(projectId, userId);

        return ResponseEntity.ok(ApiResponse.success(
                ProjectSuccessCode.PROJECT_MEMBER_LIST_RETRIEVED,
                response
        ));
    }
}
