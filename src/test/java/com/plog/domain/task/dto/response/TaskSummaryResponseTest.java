package com.plog.domain.task.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskSummaryResponseTest {

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
    @DisplayName("진행중 + 마감일 지남 -> isOverdue true (기존 동작)")
    void overdue_whenInProgressAndPastDeadline() {
        Task task = Task.builder()
                .projectMember(stubAssignee())
                .title("title")
                .cardStatus(TaskStatus.IN_PROGRESS)
                .endDate(LocalDate.now().minusDays(3))
                .build();

        TaskSummaryResponse response = TaskSummaryResponse.from(task, 0);

        assertThat(response.isOverdue()).isTrue();
    }

    @Test
    @DisplayName("예정 + 마감일 안 지남 -> isOverdue false")
    void notOverdue_whenTodoAndBeforeDeadline() {
        Task task = Task.builder()
                .projectMember(stubAssignee())
                .title("title")
                .cardStatus(TaskStatus.TODO)
                .endDate(LocalDate.now().plusDays(3))
                .build();

        TaskSummaryResponse response = TaskSummaryResponse.from(task, 0);

        assertThat(response.isOverdue()).isFalse();
    }

    @Test
    @DisplayName("완료 + 마감일 이전에 완료 -> isOverdue false (정상 완료)")
    void notOverdue_whenCompletedBeforeDeadline() {
        Task task = Task.builder()
                .projectMember(stubAssignee())
                .title("title")
                .cardStatus(TaskStatus.DONE)
                .endDate(LocalDate.now().plusDays(1))
                .completedAt(LocalDateTime.now())
                .build();

        TaskSummaryResponse response = TaskSummaryResponse.from(task, 0);

        assertThat(response.isOverdue()).isFalse();
    }

    @Test
    @DisplayName("완료 + 마감일 이후에 완료(지연 완료) -> isOverdue true 유지 (신규 요구사항)")
    void overdue_whenCompletedAfterDeadline() {
        Task task = Task.builder()
                .projectMember(stubAssignee())
                .title("title")
                .cardStatus(TaskStatus.DONE)
                .endDate(LocalDate.now().minusDays(5))
                .completedAt(LocalDateTime.now()) // 마감일보다 한참 뒤에 완료
                .build();

        TaskSummaryResponse response = TaskSummaryResponse.from(task, 0);

        assertThat(response.isOverdue()).isTrue();
    }

    @Test
    @DisplayName("완료인데 completedAt이 null인 방어 케이스 -> isOverdue false")
    void notOverdue_whenCompletedAtIsNullDefensive() {
        Task task = Task.builder()
                .projectMember(stubAssignee())
                .title("title")
                .cardStatus(TaskStatus.DONE)
                .endDate(LocalDate.now().minusDays(1))
                .completedAt(null)
                .build();

        TaskSummaryResponse response = TaskSummaryResponse.from(task, 0);

        assertThat(response.isOverdue()).isFalse();
    }
}