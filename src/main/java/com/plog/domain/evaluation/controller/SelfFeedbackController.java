package com.plog.domain.evaluation.controller;

import com.plog.domain.evaluation.dto.request.SelfFeedbackCreateRequest;
import com.plog.domain.evaluation.dto.response.SelfFeedbackResponse;
import com.plog.domain.evaluation.dto.response.SelfFeedbackUpdateResponse;
import com.plog.domain.evaluation.service.SelfFeedbackService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.EvaluationSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Self Feedback", description = "셀프 피드백 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/self-feedbacks")
public class SelfFeedbackController {

    private final SelfFeedbackService selfFeedbackService;

    @Operation(
            summary = "등록한 셀프 피드백 단건 조회",
            description = "현재 사용자가 해당 프로젝트에 등록한 셀프 피드백을 조회합니다."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SelfFeedbackResponse>> getMySelfFeedback(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        SelfFeedbackResponse response = selfFeedbackService.getMySelfFeedback(projectId, userId);

        return ResponseEntity
                .status(EvaluationSuccessCode.SELF_FEEDBACK_RETRIEVED.getHttpStatus())
                .body(ApiResponse.success(EvaluationSuccessCode.SELF_FEEDBACK_RETRIEVED, response));
    }

    @Operation(
            summary = "셀프 피드백 등록",
            description = "현재 사용자가 해당 프로젝트에 셀프 피드백을 등록합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<SelfFeedbackResponse>> createSelfFeedback(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SelfFeedbackCreateRequest request
    ) {
        SelfFeedbackResponse response = selfFeedbackService.createSelfFeedback(projectId, userId, request);

        return ResponseEntity
                .status(EvaluationSuccessCode.SELF_FEEDBACK_SUBMITTED.getHttpStatus())
                .body(ApiResponse.success(EvaluationSuccessCode.SELF_FEEDBACK_SUBMITTED, response));
    }

    @Operation(
            summary = "셀프 피드백 수정",
            description = "현재 사용자가 해당 프로젝트에 등록한 셀프 피드백을 수정합니다."
    )
    @PutMapping
    public ResponseEntity<ApiResponse<SelfFeedbackUpdateResponse>> updateSelfFeedback(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SelfFeedbackCreateRequest request
    ) {
        SelfFeedbackUpdateResponse response = selfFeedbackService.updateSelfFeedback(projectId, userId, request);

        return ResponseEntity
                .status(EvaluationSuccessCode.SELF_FEEDBACK_UPDATED.getHttpStatus())
                .body(ApiResponse.success(EvaluationSuccessCode.SELF_FEEDBACK_UPDATED, response));
    }
}
