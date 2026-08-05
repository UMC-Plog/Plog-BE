package com.plog.domain.task.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.plog.domain.project.entity.ProjectMember;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskOverdueCalculatorTest {

    private Task taskOf(TaskStatus status, LocalDate endDate, LocalDateTime completedAt) {
        return Task.builder()
                .projectMember(mock(ProjectMember.class))
                .title("title")
                .cardStatus(status)
                .endDate(endDate)
                .completedAt(completedAt)
                .build();
    }

    @Test
    @DisplayName("마감일 없음 -> false")
    void notOverdue_whenEndDateNull() {
        Task task = taskOf(TaskStatus.IN_PROGRESS, null, null);

        assertThat(TaskOverdueCalculator.isOverdue(task)).isFalse();
    }

    @Test
    @DisplayName("진행중 + 마감일 지남 -> true")
    void overdue_whenInProgressAndPastDeadline() {
        Task task = taskOf(TaskStatus.IN_PROGRESS, LocalDate.now().minusDays(3), null);

        assertThat(TaskOverdueCalculator.isOverdue(task)).isTrue();
    }

    @Test
    @DisplayName("예정 + 마감일 안 지남 -> false")
    void notOverdue_whenTodoAndBeforeDeadline() {
        Task task = taskOf(TaskStatus.TODO, LocalDate.now().plusDays(3), null);

        assertThat(TaskOverdueCalculator.isOverdue(task)).isFalse();
    }

    @Test
    @DisplayName("완료 + 마감일 이전에 완료 -> false (정상 완료)")
    void notOverdue_whenCompletedBeforeDeadline() {
        Task task = taskOf(TaskStatus.DONE, LocalDate.now().plusDays(1), LocalDateTime.now());

        assertThat(TaskOverdueCalculator.isOverdue(task)).isFalse();
    }

    @Test
    @DisplayName("완료 + 마감일 이후에 완료(지연 완료) -> true 유지 (핵심 요구사항)")
    void overdue_whenCompletedAfterDeadline() {
        Task task = taskOf(TaskStatus.DONE, LocalDate.now().minusDays(5), LocalDateTime.now());

        assertThat(TaskOverdueCalculator.isOverdue(task)).isTrue();
    }

    @Test
    @DisplayName("완료인데 completedAt이 null인 방어 케이스 -> false")
    void notOverdue_whenCompletedAtIsNullDefensive() {
        Task task = taskOf(TaskStatus.DONE, LocalDate.now().minusDays(1), null);

        assertThat(TaskOverdueCalculator.isOverdue(task)).isFalse();
    }
}