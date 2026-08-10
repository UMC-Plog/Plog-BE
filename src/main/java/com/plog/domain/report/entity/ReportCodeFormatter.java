package com.plog.domain.report.entity;

import com.plog.global.util.TimeUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ReportCodeFormatter {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private ReportCodeFormatter() {
    }

    public static String format(Long reportId, LocalDateTime createdAtUtc) {
        if (reportId == null || createdAtUtc == null) {
            return null;
        }
        String yearMonth = createdAtUtc.atZone(TimeUtil.STORAGE_ZONE)
                .withZoneSameInstant(TimeUtil.DISPLAY_ZONE)
                .format(YEAR_MONTH);
        return "PLOG-" + yearMonth + "-" + String.format(java.util.Locale.ROOT, "%08d", reportId);
    }
}
