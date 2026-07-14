package com.plog.domain.evaluation.controller;

import com.plog.domain.evaluation.dto.response.EvaluationTargetResponse;
import com.plog.domain.evaluation.service.EvaluationService;
import com.plog.global.api.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping("/targets")
    public ApiResponse<EvaluationTargetResponse> getEvaluationTargets(
            @PathVariable Long projectId
            // TODO: 현재 로그인한 유저 ID를 주입 받기

    ) {
        Long tempUserId = 1L; // Security 구현 전까지 하드코딩
        EvaluationTargetResponse response = evaluationService.getEvaluationTargets(projectId, tempUserId);

        return ApiResponse.success(response);
    }
}