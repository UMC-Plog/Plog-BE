package com.plog.domain.evaluation.dto.response;

public record EvaluationTargetResponse(
        String projectMemberId,
        String nickname,
        boolean isEvaluated
) {}