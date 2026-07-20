package com.plog.domain.evaluation.dto.response;

public record PeerEvaluationCreateResponse(
        Long peerId,
        boolean isNudgeTriggered
) {
}