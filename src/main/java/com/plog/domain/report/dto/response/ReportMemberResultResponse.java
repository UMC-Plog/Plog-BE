package com.plog.domain.report.dto.response;

import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.llm.MemberReportText;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

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
        @Schema(description = "외부 연동 활동 점수. 계정 매핑이 없거나 점수화 가능한 활동이 없으면 null", example = "74.00")
        BigDecimal externalScore,
        @Schema(description = "동료 평가 점수", example = "80.00")
        BigDecimal peerScore,
        @Schema(description = "자기 피드백 일치도 점수", example = "70.00")
        BigDecimal selfFeedbackScore,
        @Schema(description = "가중합 최종 점수", example = "82.50")
        BigDecimal finalScore,
        @Schema(description = "리포트 생성 시점에 프로젝트에 ACTIVE 외부 도구 연동이 하나라도 있었는지 여부. "
                + "true여도 멤버 계정 미매핑 또는 점수화 가능한 외부 활동 부족으로 externalScore가 null일 수 있으며, "
                + "이때 외부 가중치를 제외해 비례 재분배된 점수다", example = "true")
        boolean externalToolConnected,
        @Schema(description = "분석 신뢰도 등급", example = "P1")
        ReliabilityTier reliabilityTier,
        @Schema(description = "분석 한계 안내 문구. 없으면 null",
                example = "Notion이 연동되지 않아 일부 작업 과정은 반영되지 않았을 수 있습니다.")
        String cautionText,
        @Schema(description = "부여된 전체 업무 수", example = "13")
        int totalTaskCount,
        @Schema(description = "완료한 업무 수", example = "12")
        int completedTaskCount,
        @Schema(description = "기한 내 완료한 업무 수. \"12/13건\" 표기의 앞 숫자(분모는 totalTaskCount)",
                example = "12")
        int deadlineMetTaskCount,
        @Schema(description = "AI 한줄 평가. 개인 리포트 상단에 노출됩니다",
                example = "적극적인 리더십으로 팀의 방향을 잡고, 구성원들이 원활하게 협업할 수 있도록 분위기를 주도했어요")
        String headline,
        @Schema(description = "강점 분석 카드. 아이콘은 프론트가 순서대로 매핑합니다. 근거 부족 시 빈 배열")
        List<MemberReportText.StrengthCard> strengths,
        @Schema(description = "취약점 진단. 근거 부족 시 null")
        MemberReportText.Weakness weakness,
        @Schema(description = "AI 개인 성장 인사이트. 근거 부족 시 null")
        MemberReportText.GrowthInsight growth,
        @Schema(description = "AI 문장 변환(자기소개서/포트폴리오). 근거 부족 시 null")
        MemberReportText.WritingSuggestion writing
) {
    public ReportMemberResultResponse {
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
    }
}
