package com.plog.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReportCodeFormatterTest {

    // 저장 기준이 KST라 연월이 저장값 그대로 쓰인다. 월 경계 직전 값으로 경계를 지킨다.
    @Test
    void 생성시각의_KST_연월로_코드를_만들고_ID를_8자리로_패딩한다() {
        String code = ReportCodeFormatter.format(
                12345L, LocalDateTime.of(2025, 7, 31, 23, 59));

        assertThat(code).isEqualTo("PLOG-2025-07-00012345");
    }

    @Test
    void 자정을_넘기면_다음_달_코드가_된다() {
        String code = ReportCodeFormatter.format(
                12345L, LocalDateTime.of(2025, 8, 1, 0, 0));

        assertThat(code).isEqualTo("PLOG-2025-08-00012345");
    }
}
