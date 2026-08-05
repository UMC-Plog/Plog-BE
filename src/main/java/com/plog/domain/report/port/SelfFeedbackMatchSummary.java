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
 * @param normalizedScore 점수 조립에 쓰는 0~100 점수. 미계산이면 null
 */
public record SelfFeedbackMatchSummary(
        boolean submitted,
        int matchedCount,
        int unmatchedCount,
        int uncertainCount,
        BigDecimal matchRatio,
        BigDecimal normalizedScore
) {
    /** 자기 피드백을 제출하지 않은 멤버. */
    public static SelfFeedbackMatchSummary notSubmitted() {
        return new SelfFeedbackMatchSummary(false, 0, 0, 0, null, null);
    }
}
