package com.plog.domain.task.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.global.util.TimeUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 시각 필드는 오프셋을 실어 내보낸다.
 * 오프셋 없는 "2026-08-07T23:14:25" 가 나가면 클라이언트가 서버 타임존을 추측해야 한다.
 */
class TaskResponseTimeContractTest {

    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 8, 7, 23, 14, 25);

    @Test
    void exposesCompletedAtAsAnAbsoluteInstantInTheStatusResponse() {
        Task task = doneTaskCompletedAt(COMPLETED_AT);

        TaskStatusUpdateResponse response = TaskStatusUpdateResponse.from(task);

        assertThat(response.completedAt()).isEqualTo(TimeUtil.toInstant(COMPLETED_AT));
    }

    @Test
    void exposesCompletedAtAsAnAbsoluteInstantInTheDetailResponse() {
        Task task = doneTaskCompletedAt(COMPLETED_AT);

        TaskDetailResponse response = TaskDetailResponse.from(task, List.of());

        assertThat(response.completedAt()).isEqualTo(TimeUtil.toInstant(COMPLETED_AT));
    }

    @Test
    void leavesCompletedAtNullWhileTheCardIsNotDone() {
        Task task = Mockito.mock(Task.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(task.getCardStatus()).thenReturn(TaskStatus.IN_PROGRESS);
        Mockito.when(task.getCompletedAt()).thenReturn(null);

        assertThat(TaskStatusUpdateResponse.from(task).completedAt()).isNull();
    }

    private Task doneTaskCompletedAt(LocalDateTime completedAt) {
        Task task = Mockito.mock(Task.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(task.getId()).thenReturn(1L);
        Mockito.when(task.getTitle()).thenReturn("설계 문서 작성");
        Mockito.when(task.getCardStatus()).thenReturn(TaskStatus.DONE);
        Mockito.when(task.getEndDate()).thenReturn(null);
        Mockito.when(task.getCompletedAt()).thenReturn(completedAt);
        return task;
    }
}
