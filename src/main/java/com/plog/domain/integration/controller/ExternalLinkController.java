package com.plog.domain.integration.controller;

import com.plog.domain.integration.dto.response.ExternalLinkStatusResponse;
import com.plog.domain.integration.service.ExternalLinkService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ProjectSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "External Link", description = "외부 툴 연동 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/me/external-links")
public class ExternalLinkController {

    private final ExternalLinkService externalLinkService;

    @Operation(
            summary = "내 외부 툴 연동 상태 조회",
            description = "현재 사용자의 프로젝트 외부 툴 연동 상태를 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<ExternalLinkStatusResponse>> getMyExternalLinks(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        ExternalLinkStatusResponse response = externalLinkService.getMyExternalLinks(projectId, userId);

        return ResponseEntity
                .status(ProjectSuccessCode.EXTERNAL_LINKS_RETRIEVED.getHttpStatus())
                .body(ApiResponse.success(ProjectSuccessCode.EXTERNAL_LINKS_RETRIEVED, response));
    }
}
