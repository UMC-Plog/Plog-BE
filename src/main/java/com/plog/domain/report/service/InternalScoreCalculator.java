package com.plog.domain.report.service;

import com.plog.domain.report.entity.ActivityCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 4단계 내부 점수({@code internalScore}, 0~100) 계산기. 순수 함수 — 외부 상태 없음.
 * <p>
 * {@code internalScore = taskComponent × 0.6 + activityComponent × 0.4}
 * <ul>
 *     <li><b>taskComponent</b>: 업무 완료율·마감준수율 기반. 직접 검증 가능한 팩트라
 *     activityComponent보다 조금 더 무게를 둔다.</li>
 *     <li><b>activityComponent</b>: 2단계로 분류된 활동 유형별 건수에 가중치를 곱해 합산한 뒤
 *     포화곡선으로 0~100에 정규화한다. 2단계 분류는 임베딩 유사도 기반이라 오분류 가능성이
 *     있어 taskComponent보다 비중을 조금 낮춘다.</li>
 * </ul>
 * 업무카드가 하나도 없는 멤버(채팅 등 활동만 있는 경우)는 taskComponent를 계산에서 제외하고
 * activityComponent 100%로 점수를 낸다 — 없는 업무의 완료율을 0으로 볼 이유가 없기 때문이다.
 * <p>
 * <b>가정(실측 없이 잡음, 리포트 결과 보고 이 클래스의 상수만 조정하면 된다)</b>: 활동 유형별
 * 가중치는 PM 명세(6.1.2 활동 유형 분류)가 이미 정한 티어를 그대로 수치화했다 — 의사결정·문제해결은
 * 높은 가중치, 피드백·일정조율·산출물제출은 중간 이상, 단순응답은 낮은 가중치. 포화곡선의
 * half-point(ACTIVITY_HALF_POINT)는 {@code ActivityAnchors}의 코사인 유사도 임계값(0.5)과 같은
 * 성격의 하드코딩 상수다.
 */
final class InternalScoreCalculator {

    private static final BigDecimal COMPLETION_RATE_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal DEADLINE_RATE_WEIGHT = new BigDecimal("0.4");

    private static final BigDecimal TASK_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal ACTIVITY_WEIGHT = new BigDecimal("0.4");

    /** weightedSum이 이 값일 때 activityComponent=50. 값이 클수록 점수가 천천히 오른다. */
    private static final BigDecimal ACTIVITY_HALF_POINT = new BigDecimal("30");

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = HUNDRED;
    private static final int INTERMEDIATE_SCALE = 10;
    private static final int RESULT_SCALE = 2;

    private InternalScoreCalculator() {
    }

    /**
     * @param activityTypeSummary    2단계 분류 유형별 건수 ({@code InternalReportData.activityTypeSummary()}와 동일한 값)
     * @param totalTaskCount         부여된 전체 업무 수. 0이면 taskComponent를 제외하고 activityComponent 100%로 계산한다
     * @param completionRate         업무 완료율 0.0~1.0
     * @param deadlineComplianceRate 마감 준수율 0.0~1.0
     * @return 0~100, scale 2. 입력이 전부 비어 있어도(업무·활동 모두 0건) 예외 대신 0을 돌려준다 — 절대 null이 아니다
     */
    static BigDecimal calculate(
            Map<ActivityCategory, Integer> activityTypeSummary,
            int totalTaskCount,
            double completionRate,
            double deadlineComplianceRate
    ) {
        BigDecimal activityComponent = activityComponent(activityTypeSummary);
        if (totalTaskCount == 0) {
            return clampAndScale(activityComponent);
        }
        BigDecimal taskComponent = taskComponent(completionRate, deadlineComplianceRate);
        BigDecimal combined = taskComponent.multiply(TASK_WEIGHT)
                .add(activityComponent.multiply(ACTIVITY_WEIGHT));
        return clampAndScale(combined);
    }

    private static BigDecimal taskComponent(double completionRate, double deadlineComplianceRate) {
        BigDecimal blendedRate = BigDecimal.valueOf(completionRate).multiply(COMPLETION_RATE_WEIGHT)
                .add(BigDecimal.valueOf(deadlineComplianceRate).multiply(DEADLINE_RATE_WEIGHT));
        return blendedRate.multiply(HUNDRED);
    }

    private static BigDecimal activityComponent(Map<ActivityCategory, Integer> activityTypeSummary) {
        BigDecimal weightedSum = weightedActivitySum(activityTypeSummary);
        if (weightedSum.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // 100 * weightedSum / (weightedSum + K) — Michaelis-Menten 형태의 포화곡선.
        // weightedSum이 커질수록 완만해지며 K를 아무리 넘어서도 이론상 100을 넘지 않는다(점근).
        return weightedSum.multiply(HUNDRED)
                .divide(weightedSum.add(ACTIVITY_HALF_POINT), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal weightedActivitySum(Map<ActivityCategory, Integer> activityTypeSummary) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<ActivityCategory, Integer> entry : activityTypeSummary.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            sum = sum.add(BigDecimal.valueOf(weightOf(entry.getKey()))
                    .multiply(BigDecimal.valueOf(entry.getValue())));
        }
        return sum;
    }

    /**
     * 활동 유형별 가중치. PM 명세(6.1.2)가 이미 정한 티어를 그대로 수치화했다 — 새 기준을
     * 만들지 않고 기존 문서와 일치시켰다.
     */
    private static int weightOf(ActivityCategory category) {
        return switch (category) {
            case DECISION, PROBLEM_SOLVING -> 5;
            case FEEDBACK, SCHEDULE_COORDINATION, DELIVERABLE_SUBMIT -> 3;
            case SIMPLE_RESPONSE -> 1;
        };
    }

    private static BigDecimal clampAndScale(BigDecimal score) {
        BigDecimal clamped = score.max(MIN_SCORE).min(MAX_SCORE);
        return clamped.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }
}