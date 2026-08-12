package com.plog.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.llm.MemberReportText;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "리포트 멤버별 결과 응답")
public record ReportMemberResultResponse(
        @Schema(description = "리포트 ID", example = "20")
        Long reportId,
        @Schema(description = "프로젝트 멤버 ID", example = "7")
        Long projectMemberId,
        @Schema(description = "실명", example = "김창훈")
        String memberName,
        @Schema(description = "Plog 내부 활동 점수", example = "88.00")
        BigDecimal internalScore,
        @Schema(description = "외부 연동 활동 점수. 계정 매핑이 없거나 점수화 가능한 활동이 없으면 null", example = "74.00")
        BigDecimal externalScore,
        @Schema(description = "종합점수 계산용 동료 평가 점수(5점 평균을 0~100으로 환산)", example = "80.00")
        BigDecimal peerScore,
        @Schema(description = "종합 Peer 평균(5점 척도). 받은 평가가 없으면 null", example = "4.25")
        BigDecimal peerAverage,
        @Schema(description = "Peer 평균의 팀 내 Z-score. 팀원 1명/동점/근거 부족이면 null")
        BigDecimal peerZScore,
        @Schema(description = "Peer Z-score 기반 팀 내 percentile. 계산 불가하면 null")
        BigDecimal peerPercentile,
        @Schema(description = "역량별 평균(5점 척도). LEADERSHIP 은 주도성 점수. 근거 없으면 빈 객체",
                example = "{\"COLLABORATION\":4.4,\"LEADERSHIP\":4.2,\"COMMUNICATION\":4.0,\"OUTPUT\":4.4}")
        Map<CompetencyCategory, BigDecimal> peerCompetencyScores,
        @Schema(description = "동료 평가 키워드(태그 칩). 근거 없으면 빈 배열", example = "[\"리더십\",\"책임감\"]")
        List<String> peerKeywords,
        @Schema(description = "자기 피드백과 활동 근거의 일치도 지표. 최종 기여도 점수에는 직접 반영되지 않음", example = "70.00")
        BigDecimal selfFeedbackScore,
        @Schema(description = "가중합 최종 점수", example = "82.50")
        BigDecimal finalScore,
        @Schema(description = "팀 내 기여율. 팀 분모가 완전하고 합계가 양수일 때만 값 존재")
        BigDecimal contributionRate,
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
        @Schema(description = "기한 내 완료한 업무 수. \"12/13건\" 표기의 앞 숫자",
                example = "12")
        int deadlineMetTaskCount,
        @Schema(description = "마감 준수율 계산 대상 업무 수. \"12/13건\" 표기의 뒤 숫자", example = "13")
        int deadlineTargetTaskCount,
        @Schema(description = "업무 완료율(0~100). 업무가 없으면 null")
        BigDecimal completionRate,
        @Schema(description = "마감 준수율(0~100). 마감 대상 업무가 없으면 null")
        BigDecimal deadlineComplianceRate,
        @Schema(description = "협업 안정도(0~100). 마감/Peer 협업/소통 중 하나라도 없으면 null")
        BigDecimal collaborationStability,
        @Schema(description = "팀 percentile 기반 개선 필요도(기존 필드명 vulnerability)")
        BigDecimal vulnerability,
        @Schema(description = "가장 낮은 percentile의 역량 축. 근거 없으면 null")
        CompetencyCategory vulnerableCompetency,
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
        MemberReportText.WritingSuggestion writing,
        @Schema(description = "개인 리포트 표시 코드", example = "PLOG-P-2026-08-00000015") String reportCode,
        @Schema(description = "프로젝트 이름") String projectName,
        @Schema(description = "리포트 발행 시각") Instant completedAt,
        @Schema(description = "프로젝트 시작일") LocalDate projectStartDate,
        @Schema(description = "프로젝트 종료일") LocalDate projectEndDate,
        @Schema(description = "개인 기여도 상세 그래프용 역량 점수(0~100). Peer 역량 5점 평균을 20배 환산")
        Map<CompetencyCategory, BigDecimal> competencyScores100
) {
    public ReportMemberResultResponse {
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
        peerCompetencyScores = peerCompetencyScores == null ? Map.of() : Map.copyOf(peerCompetencyScores);
        peerKeywords = peerKeywords == null ? List.of() : List.copyOf(peerKeywords);
        competencyScores100 = competencyScores100 == null ? Map.of() : Map.copyOf(competencyScores100);
    }

    /** 기존 클라이언트 호환용 5점 척도 alias. 신규 연동은 peerCompetencyScores를 사용한다. */
    @Deprecated
    @JsonProperty("competencyScores")
    @Schema(description = "Deprecated: peerCompetencyScores와 동일한 5점 척도 값", deprecated = true)
    public Map<CompetencyCategory, BigDecimal> competencyScores() {
        return peerCompetencyScores;
    }

    public ReportMemberResultResponse(
            Long reportId,
            Long projectMemberId,
            String memberName,
            BigDecimal internalScore,
            BigDecimal externalScore,
            BigDecimal peerScore,
            BigDecimal peerAverage,
            Map<CompetencyCategory, BigDecimal> peerCompetencyScores,
            List<String> peerKeywords,
            BigDecimal selfFeedbackScore,
            BigDecimal finalScore,
            boolean externalToolConnected,
            ReliabilityTier reliabilityTier,
            String cautionText,
            int totalTaskCount,
            int completedTaskCount,
            int deadlineMetTaskCount,
            String headline,
            List<MemberReportText.StrengthCard> strengths,
            MemberReportText.Weakness weakness,
            MemberReportText.GrowthInsight growth,
            MemberReportText.WritingSuggestion writing
    ) {
        this(reportId, projectMemberId, memberName, internalScore, externalScore, peerScore,
                peerAverage, null, null, peerCompetencyScores, peerKeywords, selfFeedbackScore,
                finalScore, null, externalToolConnected, reliabilityTier, cautionText,
                totalTaskCount, completedTaskCount, deadlineMetTaskCount, totalTaskCount,
                null, null, null, null, null, headline, strengths, weakness, growth, writing,
                null, null, null, null, null, Map.of());
    }
}
