package com.plog.domain.evaluation.dto.response;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import java.util.List;
import lombok.Builder;

@Builder
public record PeerEvaluationDetailResponse(
        Long peerId,
        int collaborationScore,
        int initiativeScore,
        int responsibilityScore,
        int communicationScore,
        int outputScore,
        List<String> keyword,
        String feedback
) {
    public static PeerEvaluationDetailResponse from(PeerEvaluation evaluation) {
        return PeerEvaluationDetailResponse.builder()
                .peerId(evaluation.getId())
                .collaborationScore(evaluation.getCollaborationScore())
                .initiativeScore(evaluation.getInitiativeScore())
                .responsibilityScore(evaluation.getResponsibilityScore())
                .communicationScore(evaluation.getCommunicationScore())
                .outputScore(evaluation.getOutputScore())
                .keyword(evaluation.getKeywords())
                .feedback(evaluation.getFeedback())
                .build();
    }
}