package com.plog.domain.report.port;

import java.math.BigDecimal;

/**
 * 서희 담당 구간의 자기 피드백 일치도. 멤버가 쓴 자기 피드백 내용이 실제 활동 로그와
 * 얼마나 맞아떨어지는지를 집계한 것이다.
 *
 * @param submitted       자기 피드백을 제출했는지. false 면 나머지 값은 모두 0/null 이고,
 *                        LLM 은 자기 인식 관련 서술을 하지 않는다
 * @param matchedCount    로그로 뒷받침되는 서술 수
 * @param unmatchedCount  로그와 어긋나는 서술 수
 * @param uncertainCount  판정 불가한 서술 수
 * @param matchRatio      일치 비율 0.0~1.0. 판정 대상이 없으면 null
 * @param normalizedScore 점수 조립에 쓰는 0~100 점수. {@code EvaluationSummaryProvider} 구현체는
 *                        항상 값을 채워야 한다 — null이면 {@code ReportMemberScoreService}에서
 *                        예외가 난다. 미제출 멤버는 {@link #notSubmitted()}를 통해 0으로 채워진다
 */
public record SelfFeedbackMatchSummary(
        boolean submitted,
        int matchedCount,
        int unmatchedCount,
        int uncertainCount,
        BigDecimal matchRatio,
        BigDecimal normalizedScore
) {
    /**
     * 자기 피드백을 제출하지 않은 멤버. normalizedScore는 null이 아니라 0.00 —
     * {@code ReportMemberScoreService}가 필수값으로 요구하므로, "미제출"은 곧 "점수 0"으로 취급한다.
     */
    public static SelfFeedbackMatchSummary notSubmitted() {
        return new SelfFeedbackMatchSummary(false, 0, 0, 0, null, new BigDecimal("0.00"));
    }
}