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
        @Schema(description = "외부 서비스 본인 계정 선택 완료 여부. 최종 제출 필수 조건은 아님", example = "false")
        boolean isAccountMappingCompleted,
        @JsonProperty("isCurrentMemberFinalSubmitted")
        @Schema(description = "현재 멤버의 최종 제출 완료 여부", example = "false")
        boolean isCurrentMemberFinalSubmitted,
        @Schema(description = "최종 제출을 완료한 활성 멤버 수", example = "2")
        int completedFinalSubmissionCount,
        @Schema(description = "최종 제출 대상 활성 멤버 수", example = "3")
        int totalFinalSubmissionCount,
        @JsonProperty("isFinalSubmissionAvailable")
        @Schema(description = "Peer 평가를 완료했고 아직 최종 제출할 수 있는지 여부", example = "true")
        boolean isFinalSubmissionAvailable
) {
}
