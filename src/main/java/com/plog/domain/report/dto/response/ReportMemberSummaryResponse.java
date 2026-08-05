package com.plog.domain.report.dto.response;

import com.plog.domain.report.entity.ReliabilityTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "리포트 상세의 멤버 요약 항목")
public record ReportMemberSummaryResponse(
        @Schema(description = "프로젝트 멤버 ID", example = "7")
        Long projectMemberId,
        @Schema(description = "표시 닉네임", example = "창훈")
        String memberName,
        @Schema(description = "최종 점수. 아직 계산 전이면 null", example = "82.50")
        BigDecimal finalScore,
        @Schema(description = "분석 신뢰도 등급", example = "P1")
        ReliabilityTier reliabilityTier,
        @Schema(description = "AI 한줄 평가. 팀 리포트 멤버 카드에 노출됩니다. 생성 실패 시 null",
                example = "적극적인 리더십으로 팀의 방향을 잡고, 구성원들이 원활하게 협업할 수 있도록 분위기를 주도했어요")
        String headline
) {
}
