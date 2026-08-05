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
    @DisplayName("from()은 Task 필드를 그대로 매핑하고, isOverdue는 TaskOverdueCalculator 위임 결과를 담는다")
    void from_mapsFieldsAndDelegatesOverdueCalculation() {
        Task task = Task.builder()
                .projectMember(stubAssignee())
                .title("업무카드 제목")
                .cardStatus(TaskStatus.DONE)
                .endDate(LocalDate.now().minusDays(5))
                .completedAt(LocalDateTime.now()) // 지연 완료 -> isOverdue true여야 함
                .build();

        TaskSummaryResponse response = TaskSummaryResponse.from(task, 3);

        assertThat(response.title()).isEqualTo("업무카드 제목");
        assertThat(response.cardStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(response.attachmentCount()).isEqualTo(3);
        assertThat(response.assignee().nickname()).isEqualTo("테스터");
        assertThat(response.isOverdue()).isTrue();
    }
}