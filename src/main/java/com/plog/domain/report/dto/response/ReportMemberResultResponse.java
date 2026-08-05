package com.plog.domain.report.dto.response;

import com.plog.domain.report.entity.ReliabilityTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "리포트 멤버별 결과 응답")
public record ReportMemberResultResponse(
        @Schema(description = "리포트 ID", example = "20")
        Long reportId,
        @Schema(description = "프로젝트 멤버 ID", example = "7")
        Long projectMemberId,
        @Schema(description = "표시 닉네임", example = "창훈")
        String memberName,
        @Schema(description = "Plog 내부 활동 점수", example = "88.00")
        BigDecimal internalScore,
        @Schema(description = "외부 연동 활동 점수. 미연동이면 null", example = "74.00")
        BigDecimal externalScore,
        @Schema(description = "동료 평가 점수", example = "80.00")
        BigDecimal peerScore,
        @Schema(description = "자기 피드백 일치도 점수", example = "70.00")
        BigDecimal selfFeedbackScore,
        @Schema(description = "가중합 최종 점수", example = "82.50")
        BigDecimal finalScore,
        @Schema(description = "외부 도구 연동 여부. false면 가중치가 비례 재분배된 점수다", example = "true")
        boolean externalToolConnected,
        @Schema(description = "분석 신뢰도 등급", example = "P1")
        ReliabilityTier reliabilityTier,
        @Schema(description = "분석 한계 안내 문구. 없으면 null",
                example = "Notion이 연동되지 않아 일부 작업 과정은 반영되지 않았을 수 있습니다.")
        String cautionText
) {
}
