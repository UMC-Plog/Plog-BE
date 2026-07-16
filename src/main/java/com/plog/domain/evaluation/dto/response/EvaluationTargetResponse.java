package com.plog.domain.evaluation.dto.response;

import java.util.List;

public record EvaluationTargetResponse(
        List<TargetMemberDto> targets
) {
}