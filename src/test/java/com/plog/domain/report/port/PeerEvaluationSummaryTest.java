package com.plog.domain.report.port;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PeerEvaluationSummaryTest {

    @Test
    void none은_normalizedScore가_null이_아니라_0이다() {
        // ReportMemberScoreService.calculateAndSave가 normalizedScore를 필수값으로 요구하므로
        // "평가 없음"도 반드시 0.00을 돌려줘야 예외 없이 리포트가 발행된다.
        PeerEvaluationSummary summary = PeerEvaluationSummary.none();

        assertThat(summary.normalizedScore()).isEqualByComparingTo("0.00");
        assertThat(summary.hasEvaluation()).isFalse();
        assertThat(summary.average()).isNull();
    }
}