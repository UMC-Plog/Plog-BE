package com.plog.domain.task.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.report.entity.CompetencyCategory;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TaskCompetencyMappingTest {

    @Test
    void mapsCompetencyFieldsToTheExpectedTaskColumns() throws Exception {
        Field category = Task.class.getDeclaredField("inferredCompetency");
        Field confidence = Task.class.getDeclaredField("competencyConfidence");
        Field version = Task.class.getDeclaredField("competencyClassifierVersion");

        assertThat(category.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        assertThat(category.getAnnotation(Column.class).name()).isEqualTo("inferred_competency");
        assertThat(confidence.getAnnotation(Column.class).name()).isEqualTo("competency_confidence");
        assertThat(confidence.getAnnotation(Column.class).precision()).isEqualTo(5);
        assertThat(confidence.getAnnotation(Column.class).scale()).isEqualTo(4);
        assertThat(version.getAnnotation(Column.class).name()).isEqualTo("competency_classifier_version");
    }

    @Test
    void appliesAndClearsClassificationAsOneUnit() {
        Task task = Task.builder().build();
        task.applyCompetencyClassification(new TaskCompetencyClassification(
                CompetencyCategory.OUTPUT, new BigDecimal("0.8765"), "task-title-anchor-v1"));

        assertThat(task.getInferredCompetency()).isEqualTo(CompetencyCategory.OUTPUT);
        assertThat(task.getCompetencyConfidence()).isEqualByComparingTo("0.8765");
        assertThat(task.getCompetencyClassifierVersion()).isEqualTo("task-title-anchor-v1");

        task.clearCompetencyClassification();
        assertThat(task.getInferredCompetency()).isNull();
        assertThat(task.getCompetencyConfidence()).isNull();
        assertThat(task.getCompetencyClassifierVersion()).isNull();
    }
}
