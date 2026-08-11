package com.plog.domain.task.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.request.TaskStatusUpdateRequest;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.event.TaskStatusChangedEvent;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.domain.task.repository.TaskStatusHistoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TaskStatusServiceTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long TASK_ID = 1L;
    private static final Long USER_ID = 3L;
    private static final Long MEMBER_ID = 7L;

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TaskStatusHistoryRepository taskStatusHistoryRepository;

    private TaskStatusService service;

    @BeforeEach
    void setUp() {
        service = new TaskStatusService(
                taskRepository, projectAccessService, eventPublisher, taskStatusHistoryRepository);
    }

    private Task taskWithStatus(TaskStatus status) {
        ProjectMember member = ProjectMember.builder().id(MEMBER_ID).build();
        return Task.builder().id(TASK_ID).projectMember(member).cardStatus(status).build();
    }

    @Test
    void 상태가_실제로_바뀌면_이벤트를_발행한다() {
        Task task = taskWithStatus(TaskStatus.TODO);
        when(taskRepository.findByIdAndProjectMember_Project_Id(TASK_ID, PROJECT_ID))
                .thenReturn(Optional.of(task));

        service.updateTaskStatus(PROJECT_ID, TASK_ID, USER_ID, new TaskStatusUpdateRequest(TaskStatus.DONE));

        ArgumentCaptor<TaskStatusChangedEvent> captor = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TaskStatusChangedEvent event = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(event.taskId()).isEqualTo(TASK_ID);
        org.assertj.core.api.Assertions.assertThat(event.projectMemberId()).isEqualTo(MEMBER_ID);
        org.assertj.core.api.Assertions.assertThat(event.previousStatus()).isEqualTo(TaskStatus.TODO);
        org.assertj.core.api.Assertions.assertThat(event.newStatus()).isEqualTo(TaskStatus.DONE);
        // DONE 전이는 completedAt을 그대로 재사용해야 한다 — 재수집 스케줄러가 completedAt으로
        // "이미 적재됐는지"를 판정하므로, 정상 경로가 별도의 nowUtc()를 쓰면 값이 어긋나
        // 안전망이 무력화된다.
        org.assertj.core.api.Assertions.assertThat(event.occurredAt()).isEqualTo(task.getCompletedAt());
        org.assertj.core.api.Assertions.assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    void DONE이_아닌_전이는_completedAt이_없어_현재_시각을_이벤트에_담는다() {
        Task task = taskWithStatus(TaskStatus.TODO);
        when(taskRepository.findByIdAndProjectMember_Project_Id(TASK_ID, PROJECT_ID))
                .thenReturn(Optional.of(task));

        service.updateTaskStatus(
                PROJECT_ID, TASK_ID, USER_ID, new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS));

        ArgumentCaptor<TaskStatusChangedEvent> captor = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TaskStatusChangedEvent event = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(task.getCompletedAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void 같은_상태로_바꾸면_이벤트를_발행하지_않는다() {
        Task task = taskWithStatus(TaskStatus.DONE);
        when(taskRepository.findByIdAndProjectMember_Project_Id(TASK_ID, PROJECT_ID))
                .thenReturn(Optional.of(task));

        service.updateTaskStatus(PROJECT_ID, TASK_ID, USER_ID, new TaskStatusUpdateRequest(TaskStatus.DONE));

        verify(eventPublisher, never()).publishEvent(any());
    }
}
