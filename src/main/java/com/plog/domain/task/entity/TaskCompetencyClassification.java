package com.plog.domain.task.entity;

import com.plog.domain.report.entity.CompetencyCategory;
import java.math.BigDecimal;

/** 업무 제목 분류 결과 값 객체. */
public record TaskCompetencyClassification(
        CompetencyCategory competency,
        BigDecimal confidence,
        String classifierVersion
) {
    public static TaskCompetencyClassification unclassified() {
        return new TaskCompetencyClassification(null, null, null);
    }
}
