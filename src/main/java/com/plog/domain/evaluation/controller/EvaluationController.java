package com.plog.domain.evaluation.controller;

import com.plog.domain.evaluation.dto.response.EvaluationTargetListResponse;
import com.plog.domain.evaluation.service.EvaluationService;
import com.plog.global.api.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping("/targets")
    public ResponseEntity<ApiResponse<EvaluationTargetListResponse>> getEvaluationTargets(
            @PathVariable String projectId,
            Authentication authentication) {

        String userId = authentication.getName();
        EvaluationTargetListResponse response = evaluationService.getEvaluationTargets(projectId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
