package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.report.entity.ActivityCategory;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link InternalScoreCalculator}는 package-private 순수 함수라 리플렉션 없이 같은 패키지에서
 * 직접 테스트한다. 모든 기대값은 손으로 계산한 값이다(임베딩처럼 의미 없는 값이 아니라 실제
 * 산식이므로 계산 과정을 각 테스트 주석에 남긴다).
 */
class InternalScoreCalculatorTest {

    @Test
    void 업무만_있고_활동이_없으면_taskComponent만_반영된다() {
        // taskComponent = 100*(0.6*1.0 + 0.4*1.0) = 100.00
        // combined = 100.00*0.6 + 0(activityComponent)*0.4 = 60.00
        BigDecimal result = InternalScoreCalculator.calculate(Map.of(), 5, 1.0, 1.0);

        assertThat(result).isEqualByComparingTo("60.00");
    }

    @Test
    void 업무카드가_없으면_activityComponent_100퍼센트로_계산한다() {
        // DECISION 가중치 5 * 2건 = weightedSum 10
        // activityComponent = 100*10/(10+30) = 1000/40 = 25.00
        Map<ActivityCategory, Integer> summary = new EnumMap<>(ActivityCategory.class);
        summary.put(ActivityCategory.DECISION, 2);

        BigDecimal result = InternalScoreCalculator.calculate(summary, 0, 0.0, 0.0);

        assertThat(result).isEqualByComparingTo("25.00");
    }

    @Test
    void 업무도_활동도_없으면_0을_돌려준다() {
        BigDecimal result = InternalScoreCalculator.calculate(Map.of(), 0, 0.0, 0.0);

        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    void 가중치가_높은_활동_유형이_더_큰_점수를_만든다() {
        Map<ActivityCategory, Integer> decisionHeavy = new EnumMap<>(ActivityCategory.class);
        decisionHeavy.put(ActivityCategory.DECISION, 3); // weightedSum = 5*3 = 15
        Map<ActivityCategory, Integer> simpleResponseHeavy = new EnumMap<>(ActivityCategory.class);
        simpleResponseHeavy.put(ActivityCategory.SIMPLE_RESPONSE, 3); // weightedSum = 1*3 = 3

        BigDecimal decisionScore = InternalScoreCalculator.calculate(decisionHeavy, 0, 0.0, 0.0);
        BigDecimal simpleResponseScore = InternalScoreCalculator.calculate(simpleResponseHeavy, 0, 0.0, 0.0);

        // 100*15/45 = 33.33..., 100*3/33 = 9.09...
        assertThat(decisionScore).isEqualByComparingTo("33.33");
        assertThat(simpleResponseScore).isEqualByComparingTo("9.09");
        assertThat(decisionScore).isGreaterThan(simpleResponseScore);
    }

    @Test
    void 활동_건수가_많아도_100을_넘지_않는다() {
        // weightedSum = 5*1000 = 5000 → 100*5000/5030 = 99.4035...% → 점근할 뿐 100은 못 넘음
        Map<ActivityCategory, Integer> summary = new EnumMap<>(ActivityCategory.class);
        summary.put(ActivityCategory.DECISION, 1000);

        BigDecimal result = InternalScoreCalculator.calculate(summary, 0, 0.0, 0.0);

        assertThat(result).isLessThanOrEqualTo(new BigDecimal("100.00"));
        assertThat(result).isGreaterThan(new BigDecimal("99.00"));
    }

    @Test
    void 결과는_소수점_둘째자리로_반올림된다() {
        // SIMPLE_RESPONSE 가중치 1 * 1건 = weightedSum 1 → 100*1/31 = 3.225806451... → HALF_UP → 3.23
        Map<ActivityCategory, Integer> summary = new EnumMap<>(ActivityCategory.class);
        summary.put(ActivityCategory.SIMPLE_RESPONSE, 1);

        BigDecimal result = InternalScoreCalculator.calculate(summary, 0, 0.0, 0.0);

        assertThat(result).isEqualByComparingTo("3.23");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void 건수가_0이거나_음수인_유형은_가중치_합산에서_제외된다() {
        Map<ActivityCategory, Integer> summary = new EnumMap<>(ActivityCategory.class);
        summary.put(ActivityCategory.DECISION, 0);
        summary.put(ActivityCategory.SIMPLE_RESPONSE, 1);

        BigDecimal result = InternalScoreCalculator.calculate(summary, 0, 0.0, 0.0);

        // DECISION(건수 0)은 무시되고 SIMPLE_RESPONSE(1건, 가중치 1)만 반영 → 3.23 (위 테스트와 동일)
        assertThat(result).isEqualByComparingTo("3.23");
    }

    @Test
    void 업무와_활동이_모두_있으면_두_컴포넌트를_60대40으로_결합한다() {
        // taskComponent = 100*(0.6*0.5 + 0.4*0.5) = 50.00
        // DECISION 가중치 5*2건 = weightedSum 10 → activityComponent = 1000/40 = 25.00
        // combined = 50.00*0.6 + 25.00*0.4 = 30.00 + 10.00 = 40.00
        Map<ActivityCategory, Integer> summary = new EnumMap<>(ActivityCategory.class);
        summary.put(ActivityCategory.DECISION, 2);

        BigDecimal result = InternalScoreCalculator.calculate(summary, 4, 0.5, 0.5);

        assertThat(result).isEqualByComparingTo("40.00");
    }
}