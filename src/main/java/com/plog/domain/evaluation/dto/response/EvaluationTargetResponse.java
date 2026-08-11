package com.plog.domain.evaluation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Peer 평가 화면 진행 상태")
public record EvaluationTargetResponse(
        @Schema(description = "본인을 제외한 Peer 평가 대상 목록")
        List<TargetMemberDto> targets,
        @Schema(description = "완료한 Peer 평가 수", example = "1")
        int completedPeerEvaluationCount,
        @Schema(description = "제출해야 하는 Peer 평가 수", example = "3")
        int totalPeerEvaluationCount,
        @JsonProperty("isSelfFeedbackCompleted")
        @Schema(description = "자기 피드백 작성 완료 여부", example = "true")
        boolean isSelfFeedbackCompleted,
        @JsonProperty("isAccountMappingCompleted")
        @Schema(description = "연동된 모든 외부 서비스에서 본인 계정 선택을 완료했는지 여부", example = "true")
        boolean isAccountMappingCompleted,
        @JsonProperty("isFinalSubmissionAvailable")
        @Schema(description = "Peer 평가, 자기 피드백, 본인 계정 선택을 모두 완료해 최종 제출할 수 있는지 여부", example = "true")
        boolean isFinalSubmissionAvailable
) {
}
