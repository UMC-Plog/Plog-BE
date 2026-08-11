package com.plog.domain.task.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.entity.TaskCompetencyClassification;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskDetailResponseTest {

    @Test
    void includesTaskCompetencyClassification() {
        Task task = Task.builder()
                .projectMember(stubAssignee())
                .title("로그인 API 구현")
                .cardStatus(TaskStatus.TODO)
                .endDate(LocalDate.now())
                .build();
        task.applyCompetencyClassification(new TaskCompetencyClassification(
                CompetencyCategory.OUTPUT, new BigDecimal("0.9000"), "task-title-anchor-v1"));

        TaskDetailResponse response = TaskDetailResponse.from(task, List.of());

        assertThat(response.inferredCompetency()).isEqualTo(CompetencyCategory.OUTPUT);
        assertThat(response.competencyConfidence()).isEqualByComparingTo("0.9000");
        assertThat(response.competencyClassifierVersion()).isEqualTo("task-title-anchor-v1");
    }

    private ProjectMember stubAssignee() {
        User user = mock(User.class);
        when(user.getProfilePreset()).thenReturn(ProfilePreset.OTTER);

        ProjectMember member = mock(ProjectMember.class);
        when(member.getId()).thenReturn(1L);
        when(member.getDisplayNickname()).thenReturn("테스터");
        when(member.getUser()).thenReturn(user);
        return member;
    }

    @Test
    @DisplayName("완료 + 마감일 이후 완료 -> isOverdue true 유지, isImminent는 false, completedAt은 노출")
    void overdue_whenCompletedAfterDeadline_andNotImminent() {
        Task task = Task.builder()
                .projectMember(stubAssignee())
                .title("title")
                .cardStatus(TaskStatus.DONE)
                .endDate(LocalDate.now().minusDays(5))
                .completedAt(LocalDateTime.now())
                .build();

        TaskDetailResponse response = TaskDetailResponse.from(task, List.of());

        assertThat(response.isOverdue()).isTrue();
        assertThat(response.isImminent()).isFalse();
        assertThat(response.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("미완료 + 마감일 지남 -> isOverdue true, completedAt은 null")
    void overdue_whenNotDoneAndPastDeadline() {
        Task task = Task.builder()
                .projectMember(stubAssignee())
                .title("title")
                .cardStatus(TaskStatus.IN_PROGRESS)
                .endDate(LocalDate.now().minusDays(1))
                .build();

        TaskDetailResponse response = TaskDetailResponse.from(task, List.of());

        assertThat(response.isOverdue()).isTrue();
        assertThat(response.completedAt()).isNull();
    }
}
