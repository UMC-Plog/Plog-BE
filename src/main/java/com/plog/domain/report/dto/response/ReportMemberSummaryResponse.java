package com.plog.domain.report.dto.response;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "리포트 상세의 멤버 요약 항목")
public record ReportMemberSummaryResponse(
        @Schema(description = "프로젝트 멤버 ID", example = "7")
        Long projectMemberId,
        @Schema(description = "실명", example = "김창훈")
        String memberName,
        @Schema(description = "최종 점수. 아직 계산 전이면 null", example = "82.50")
        BigDecimal finalScore,
        @Schema(description = "팀 내 기여율. 계산 불가하면 null", example = "25.00")
        BigDecimal contributionRate,
        @Schema(description = "분석 신뢰도 등급", example = "P1")
        ReliabilityTier reliabilityTier,
        @Schema(description = "AI 한줄 평가. 팀 리포트 멤버 카드에 노출됩니다. 생성 실패 시 null",
                example = "적극적인 리더십으로 팀의 방향을 잡고, 구성원들이 원활하게 협업할 수 있도록 분위기를 주도했어요")
        String headline,
        @Schema(description = "프로필 프리셋. null이면 기본 아바타")
        ProfilePreset profilePreset,
        @Schema(description = "배정 업무 수") int totalTaskCount,
        @Schema(description = "완료 업무 수") int completedTaskCount,
        @Schema(description = "기한 내 완료 업무 수") int deadlineMetTaskCount,
        @Schema(description = "마감 대상 업무 수") int deadlineTargetTaskCount,
        @Schema(description = "멤버 업무 완료율(0~100). 업무가 없으면 null")
        BigDecimal completionRate,
        @Schema(description = "멤버 마감 준수율(0~100). 마감 대상 업무가 없으면 null")
        BigDecimal deadlineComplianceRate,
        @Schema(description = "종합 Peer 평균(5점 척도). 받은 평가가 없으면 null")
        BigDecimal peerAverage,
        @Schema(description = "역량별 Peer 평균(5점 척도)")
        Map<CompetencyCategory, BigDecimal> peerCompetencyScores,
        @Schema(description = "Peer 평가 키워드")
        List<String> peerKeywords
) {
    public ReportMemberSummaryResponse {
        peerCompetencyScores = peerCompetencyScores == null ? Map.of() : Map.copyOf(peerCompetencyScores);
        peerKeywords = peerKeywords == null ? List.of() : List.copyOf(peerKeywords);
    }

    public ReportMemberSummaryResponse(
            Long projectMemberId, String memberName, BigDecimal finalScore,
            ReliabilityTier reliabilityTier, String headline
    ) {
        this(projectMemberId, memberName, finalScore, null, reliabilityTier, headline,
                null, 0, 0, 0, 0, null, null, null, Map.of(), List.of());
    }

    public ReportMemberSummaryResponse(
            Long projectMemberId, String memberName, BigDecimal finalScore,
            BigDecimal contributionRate, ReliabilityTier reliabilityTier, String headline
    ) {
        this(projectMemberId, memberName, finalScore, contributionRate, reliabilityTier, headline,
                null, 0, 0, 0, 0, null, null, null, Map.of(), List.of());
    }
}
