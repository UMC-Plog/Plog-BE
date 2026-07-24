package com.plog.domain.project.controller;

import com.plog.domain.project.controller.docs.ProjectListControllerDoc;
import com.plog.domain.project.dto.response.ProjectListResponse;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.service.ProjectListService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import com.plog.global.api.response.SliceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectListController implements ProjectListControllerDoc {

    private final ProjectListService projectListService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<SliceResponse<ProjectListResponse>>> getProjects(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        SliceResponse<ProjectListResponse> response = projectListService.getProjects(
                userId,
                status,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(ProjectSuccessCode.PROJECT_LIST_RETRIEVED, response));
    }
}
