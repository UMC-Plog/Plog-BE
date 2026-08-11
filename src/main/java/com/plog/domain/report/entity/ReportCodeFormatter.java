package com.plog.domain.report.entity;

import com.plog.global.util.TimeUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ReportCodeFormatter {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private ReportCodeFormatter() {
    }

    public static String formatTeam(Long projectId, LocalDateTime createdAtUtc) {
        return format("T", projectId, createdAtUtc);
    }

    public static String formatPersonal(Long projectId, LocalDateTime createdAtUtc) {
        return format("P", projectId, createdAtUtc);
    }

    private static String format(String reportType, Long projectId, LocalDateTime createdAtUtc) {
        if (projectId == null || createdAtUtc == null) {
            return null;
        }
        String yearMonth = createdAtUtc.atZone(TimeUtil.STORAGE_ZONE)
                .withZoneSameInstant(TimeUtil.DISPLAY_ZONE)
                .format(YEAR_MONTH);
        return "PLOG-" + reportType + "-" + yearMonth + "-" + projectId;
    }
}
