package com.plog.domain.evaluation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SelfFeedbackCreateRequest(
        @NotBlank(message = "셀프 피드백 내용을 입력해야 합니다.")
        String content
) {
}