package com.plog.domain.evaluation.controller;

import com.plog.domain.evaluation.dto.response.SelfFeedbackResponse;
import com.plog.domain.evaluation.service.SelfFeedbackService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.EvaluationSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Self Feedback", description = "셀프 피드백 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/self-feedbacks")
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
}