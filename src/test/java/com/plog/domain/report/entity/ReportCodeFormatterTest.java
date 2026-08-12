package com.plog.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ReportCodeFormatterTest {

    @Test
    void 팀_리포트_코드는_KST_연월과_프로젝트_ID를_사용한다() {
        String code = ReportCodeFormatter.formatTeam(
                15L, LocalDateTime.of(2025, 7, 31, 15, 0));

        assertThat(code).isEqualTo("PLOG-T-2025-07-00000015");
    }

    @Test
    void 개인_리포트_코드는_P_구분자를_사용한다() {
        String code = ReportCodeFormatter.formatPersonal(
                15L, LocalDateTime.of(2025, 7, 31, 15, 0));

        assertThat(code).isEqualTo("PLOG-P-2025-07-00000015");
    }

    @Test
    void 프로젝트_ID가_8자리를_넘으면_자르지_않는다() {
        String code = ReportCodeFormatter.formatTeam(
                123456789L, LocalDateTime.of(2025, 7, 31, 15, 0));

        assertThat(code).isEqualTo("PLOG-T-2025-07-123456789");
    }

    @Test
    void 기본_Locale과_무관하게_ASCII_숫자를_사용한다() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));

            String code = ReportCodeFormatter.formatTeam(
                    15L, LocalDateTime.of(2025, 7, 31, 15, 0));

            assertThat(code).isEqualTo("PLOG-T-2025-07-00000015");
        } finally {
            Locale.setDefault(original);
        }
    }
}
