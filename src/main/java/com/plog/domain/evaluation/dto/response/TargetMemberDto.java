package com.plog.domain.evaluation.dto.response;

import lombok.Builder;

@Builder
public record TargetMemberDto(
        Long projectMemberId,
        String nickname,
        boolean isEvaluated
) {
}