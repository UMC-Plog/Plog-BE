package com.plog.domain.evaluation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PeerEvaluationCreateRequest(
        @Min(value = 1, message = "평가 점수는 1~5점 사이여야 합니다.")
        @Max(value = 5, message = "평가 점수는 1~5점 사이여야 합니다.")
        int collaborationScore,

        @Min(1) @Max(5) int initiativeScore,
        @Min(1) @Max(5) int responsibilityScore,
        @Min(1) @Max(5) int communicationScore,
        @Min(1) @Max(5) int outputScore,

        @NotEmpty(message = "최소 하나의 키워드를 선택해주세요.")
        List<String> keywords,

        @NotBlank(message = "상세 피드백은 필수 입력 항목입니다.")
        String feedback
) {
}