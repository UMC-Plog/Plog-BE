package com.plog.domain.evaluation.controller;

import com.plog.domain.evaluation.dto.request.PeerEvaluationCreateRequest;
import com.plog.domain.evaluation.dto.response.EvaluationTargetResponse;
import com.plog.domain.evaluation.dto.response.PeerEvaluationCreateResponse;
import com.plog.domain.evaluation.dto.response.PeerEvaluationDetailResponse;
import com.plog.domain.evaluation.service.EvaluationService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.EvaluationSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping("/targets")
    public ApiResponse<EvaluationTargetResponse> getEvaluationTargets(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        EvaluationTargetResponse response = evaluationService.getEvaluationTargets(projectId, userId);

        return ApiResponse.success(response);
    }

    @Operation(summary = "등록한 동료 평가 단건 상세 조회", description = "특정 팀원에게 등록한 동료 평가 상세 내역을 조회합니다.")
    @GetMapping("/peers/{targetMemberId}")
    public ResponseEntity<ApiResponse<PeerEvaluationDetailResponse>> getPeerEvaluationDetail(
            @PathVariable Long projectId,
            @PathVariable Long targetMemberId,
            @AuthenticationPrincipal Long userId
    ) {
        PeerEvaluationDetailResponse response = evaluationService.getPeerEvaluationDetail(projectId, targetMemberId, userId);

        return ResponseEntity
                .status(EvaluationSuccessCode.PEER_EVALUATION_RETRIEVED.getHttpStatus())
                .body(ApiResponse.success(EvaluationSuccessCode.PEER_EVALUATION_RETRIEVED, response));
    }

    @Operation(summary = "동료 평가 제출", description = "동료 평가 점수와 피드백을 제출합니다.")
    @PostMapping("/peers/{targetMemberId}")
    public ResponseEntity<ApiResponse<PeerEvaluationCreateResponse>> createPeerEvaluation(
            @PathVariable Long projectId,
            @PathVariable Long targetMemberId,
            @Valid @RequestBody PeerEvaluationCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        PeerEvaluationCreateResponse response = evaluationService.createPeerEvaluation(
                projectId, targetMemberId, userId, request
        );

        return ResponseEntity
                .status(EvaluationSuccessCode.EVALUATION_SUBMITTED.getHttpStatus())
                .body(ApiResponse.success(EvaluationSuccessCode.EVALUATION_SUBMITTED, response));
    }

    @Operation(summary = "동료 평가 수정", description = "등록한 동료 평가 점수와 피드백을 수정합니다.")
    @PutMapping("/peers/{targetMemberId}")
    public ResponseEntity<ApiResponse<PeerEvaluationCreateResponse>> updatePeerEvaluation(
            @PathVariable Long projectId,
            @PathVariable Long targetMemberId,
            @Valid @RequestBody PeerEvaluationCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        PeerEvaluationCreateResponse response = evaluationService.updatePeerEvaluation(
                projectId, targetMemberId, userId, request
        );

        return ResponseEntity
                .status(EvaluationSuccessCode.EVALUATION_UPDATED.getHttpStatus())
                .body(ApiResponse.success(EvaluationSuccessCode.EVALUATION_UPDATED, response));
    }
}
