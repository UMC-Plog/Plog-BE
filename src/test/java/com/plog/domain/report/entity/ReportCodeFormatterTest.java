package com.plog.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReportCodeFormatterTest {

    @Test
    void 팀_리포트_코드는_KST_연월과_프로젝트_ID를_사용한다() {
        String code = ReportCodeFormatter.formatTeam(
                15L, LocalDateTime.of(2025, 7, 31, 15, 0));

        assertThat(code).isEqualTo("PLOG-T-2025-08-15");
    }

    @Test
    void 개인_리포트_코드는_P_구분자를_사용한다() {
        String code = ReportCodeFormatter.formatPersonal(
                15L, LocalDateTime.of(2025, 7, 31, 15, 0));

        assertThat(code).isEqualTo("PLOG-P-2025-08-15");
    }
}
