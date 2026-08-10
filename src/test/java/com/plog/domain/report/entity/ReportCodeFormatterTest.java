package com.plog.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReportCodeFormatterTest {

    @Test
    void 생성시각을_KST_연월로_변환하고_ID를_8자리로_패딩한다() {
        String code = ReportCodeFormatter.format(
                12345L, LocalDateTime.of(2025, 7, 31, 15, 0));

        assertThat(code).isEqualTo("PLOG-2025-08-00012345");
    }
}
