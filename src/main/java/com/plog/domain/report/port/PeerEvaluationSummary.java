package com.plog.domain.report.port;

import com.plog.domain.report.entity.CompetencyCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 서희 담당 구간의 Peer 평가 집계. 멤버 1명이 <b>받은</b> 평가의 요약이다.
 * <p>
 * 척도 주의: {@code average} 와 {@code categoryScores} 는 화면 표기와 같은 <b>5점 만점</b>이고
 * ({@code PeerEvaluation} 의 원본 컬럼 척도), {@code normalizedScore} 만 점수 조립용
 * <b>100점 만점</b>이다. 둘을 섞으면 최종 점수가 20배 어긋난다.
 *
 * @param average         종합 Peer 평균 (0.0~5.0). 받은 평가가 없으면 null
 * @param categoryScores  역량별 평균 (0.0~5.0). 평가가 없는 역량은 키를 생략한다
 * @param evaluatorCount  이 멤버를 평가한 사람 수. 0 이면 Peer 근거가 없다는 뜻이므로
 *                        LLM 은 동료 평가 관련 서술을 하지 않는다
 * @param keywords        평가자들이 고른 키워드(예: "리더십", "책임감"). 화면의 태그 칩에 대응한다
 * @param normalizedScore Z-score 보정을 거친 0~100 점수. {@code EvaluationSummaryProvider} 구현체는
 *                        항상 값을 채워야 한다 — null이면 {@code ReportMemberScoreService}에서
 *                        예외가 난다. 평가를 하나도 못 받은 멤버는 {@link #none()}을 통해 0으로 채워진다
 */
public record PeerEvaluationSummary(
        BigDecimal average,
        Map<CompetencyCategory, BigDecimal> categoryScores,
        int evaluatorCount,
        List<String> keywords,
        BigDecimal normalizedScore
) {
    public PeerEvaluationSummary {
        categoryScores = categoryScores == null ? Map.of() : Map.copyOf(categoryScores);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    /**
     * 아직 아무에게도 평가받지 못한 멤버. normalizedScore는 null이 아니라 0.00 —
     * {@code ReportMemberScoreService}가 필수값으로 요구하므로, "평가 없음"은 곧 "점수 0"으로 취급한다.
     */
    public static PeerEvaluationSummary none() {
        return new PeerEvaluationSummary(null, Map.of(), 0, List.of(), new BigDecimal("0.00"));
    }

    public boolean hasEvaluation() {
        return evaluatorCount > 0;
    }
}