package com.plog.domain.task.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.task.entity.Task;
import com.plog.domain.user.entity.User;
import org.junit.jupiter.api.Test;

class TaskAssigneeNicknameFallbackTest {

    @Test
    void 목록과_상세_담당자는_공백_프로젝트_별칭_대신_기본_닉네임을_사용한다() {
        User user = mock(User.class);
        given(user.getNickname()).willReturn("기본 닉네임");
        ProjectMember member = ProjectMember.builder()
                .id(42L)
                .user(user)
                .anNickname(" ")
                .build();
        Task task = mock(Task.class);
        given(task.getProjectMember()).willReturn(member);

        assertThat(TaskSummaryResponse.AssigneeResponse.from(task).nickname()).isEqualTo("기본 닉네임");
        assertThat(TaskDetailResponse.AssigneeResponse.from(task).nickname()).isEqualTo("기본 닉네임");
    }
}
