package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskActivityLogServiceTest {
    @Mock private ReportActivityLogRepository activityLogRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private TaskRepository taskRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TaskActivityLogService service;

    @BeforeEach
    void setUp() {
        service = new TaskActivityLogService(
                activityLogRepository, projectMemberRepository, taskRepository, objectMapper);
    }

    @Test
    void 상태변경을_활동_로그로_적재하고_linkedTask를_바로_채운다() {
        ProjectMember member = ProjectMember.builder().id(7L).build();
        Task task = Task.builder().id(1L).build();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 5, 10, 0);
        String sourceRefId = "task-status:1:" + occurredAt;
        when(activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.TASK, sourceRefId))
                .thenReturn(false);
        when(projectMemberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(taskRepository.getReferenceById(1L)).thenReturn(task);

        service.collectStatusChanged(1L, 7L, TaskStatus.IN_PROGRESS, TaskStatus.DONE, occurredAt);

        verify(activityLogRepository).acquireSourceLock("TASK:" + sourceRefId);
        ArgumentCaptor<ReportActivityLog> captor = ArgumentCaptor.forClass(ReportActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        ReportActivityLog saved = captor.getValue();
        assertThat(saved.getSourceDomain()).isEqualTo(SourceDomain.TASK);
        assertThat(saved.getRawActivityType()).isEqualTo(RawActivityType.TASK_STATUS_CHANGE);
        assertThat(saved.getSourceRefId()).isEqualTo(sourceRefId);
        assertThat(saved.getContent()).isNull();
        assertThat(saved.getMetadata()).isEqualTo(
                "{\"schemaVersion\":1,\"taskId\":1,\"previousStatus\":\"IN_PROGRESS\",\"newStatus\":\"DONE\"}");
        assertThat(saved.getLinkedTask()).isSameAs(task);
    }

    @Test
    void previousStatus가_없어도_적재한다() {
        // 안전망 재수집 경로는 Task가 상태 이력을 저장하지 않아 previousStatus를 모른 채로 부른다.
        ProjectMember member = ProjectMember.builder().id(7L).build();
        Task task = Task.builder().id(1L).build();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 5, 10, 0);
        String sourceRefId = "task-status:1:" + occurredAt;
        when(activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.TASK, sourceRefId))
                .thenReturn(false);
        when(projectMemberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(taskRepository.getReferenceById(1L)).thenReturn(task);

        service.collectStatusChanged(1L, 7L, null, TaskStatus.DONE, occurredAt);

        ArgumentCaptor<ReportActivityLog> captor = ArgumentCaptor.forClass(ReportActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isEqualTo(
                "{\"schemaVersion\":1,\"taskId\":1,\"previousStatus\":null,\"newStatus\":\"DONE\"}");
    }

    @Test
    void 첨부_추가를_활동_로그로_적재한다() {
        ProjectMember member = ProjectMember.builder().id(7L).build();
        Task task = Task.builder().id(1L).build();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 5, 11, 0);
        when(activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.TASK, "task-attachment:9"))
                .thenReturn(false);
        when(projectMemberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(taskRepository.getReferenceById(1L)).thenReturn(task);

        service.collectAttachmentAdded(9L, 1L, 7L, occurredAt);

        verify(activityLogRepository).acquireSourceLock("TASK:task-attachment:9");
        ArgumentCaptor<ReportActivityLog> captor = ArgumentCaptor.forClass(ReportActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        ReportActivityLog saved = captor.getValue();
        assertThat(saved.getRawActivityType()).isEqualTo(RawActivityType.TASK_ATTACHMENT_ADD);
        assertThat(saved.getSourceRefId()).isEqualTo("task-attachment:9");
        assertThat(saved.getContent()).isNull();
        assertThat(saved.getMetadata()).isEqualTo("{\"schemaVersion\":1,\"taskId\":1,\"attachmentId\":9}");
        assertThat(saved.getLinkedTask()).isSameAs(task);
    }

    @Test
    void 같은_원본_이벤트는_중복_저장하지_않는다() {
        LocalDateTime occurredAt = LocalDateTime.now();
        when(activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.TASK, "task-attachment:9"))
                .thenReturn(true);

        service.collectAttachmentAdded(9L, 1L, 7L, occurredAt);

        verify(activityLogRepository).acquireSourceLock("TASK:task-attachment:9");
        verify(projectMemberRepository, never()).findById(7L);
        verify(taskRepository, never()).getReferenceById(1L);
        verify(activityLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}