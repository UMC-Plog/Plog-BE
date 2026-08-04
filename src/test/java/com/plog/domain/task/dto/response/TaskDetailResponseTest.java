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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskDetailResponseTest {

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