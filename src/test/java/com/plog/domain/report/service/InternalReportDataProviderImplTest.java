package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.TaskSummary;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalReportDataProviderImplTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long PROJECT_MEMBER_ID = 7L;

    @Mock private TaskRepository taskRepository;
    @Mock private TaskAttachmentRepository taskAttachmentRepository;

    private InternalReportDataProviderImpl provider;

    private InternalReportDataProviderImpl newProvider() {
        return new InternalReportDataProviderImpl(taskRepository, taskAttachmentRepository);
    }

    private Task taskOf(Long id, String title, TaskCategory category, TaskStatus status,
                        LocalDate endDate, LocalDateTime completedAt) {
        return Task.builder()
                .id(id)
                .projectMember(mockMember())
                .title(title)
                .category(category)
                .cardStatus(status)
                .endDate(endDate)
                .completedAt(completedAt)
                .build();
    }

    private ProjectMember mockMember() {
        return org.mockito.Mockito.mock(ProjectMember.class);
    }

    @Test
    void 활동_업무가_하나도_없으면_예외_대신_empty를_돌려준다() {
        provider = newProvider();
        when(taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(PROJECT_MEMBER_ID))
                .thenReturn(List.of());

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        assertThat(result).isEqualTo(InternalReportData.empty());
    }

    @Test
    void 업무_집계와_기한내_완료_건수를_계산한다() {
        provider = newProvider();
        // 1) 기한 내 완료, 2) 기한 지나서 완료(지연 완료), 3) 진행중(미완료), 4) 마감일 없음(완료)
        List<Task> tasks = List.of(
                taskOf(1L, "로그인 API", TaskCategory.DEVELOP, TaskStatus.DONE,
                        LocalDate.of(2026, 6, 10), LocalDateTime.of(2026, 6, 9, 0, 0)),
                taskOf(2L, "배포 스크립트", TaskCategory.DEVELOP, TaskStatus.DONE,
                        LocalDate.of(2026, 6, 1), LocalDateTime.of(2026, 6, 10, 0, 0)),
                taskOf(3L, "리포트 화면 기획", TaskCategory.PLANNING, TaskStatus.IN_PROGRESS,
                        LocalDate.of(2026, 7, 1), null),
                taskOf(4L, "회고 정리", TaskCategory.ETC, TaskStatus.DONE,
                        null, LocalDateTime.of(2026, 6, 12, 0, 0))
        );
        when(taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(PROJECT_MEMBER_ID))
                .thenReturn(tasks);
        when(taskAttachmentRepository.countByTaskIds(ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        assertThat(result.totalTaskCount()).isEqualTo(4);
        assertThat(result.completedTaskCount()).isEqualTo(3);
        // 마감 준수는 tasks[0]만 해당 (지연 완료·미완료·마감일 없음은 제외)
        assertThat(result.taskCardSummary().stream().filter(TaskSummary::metDeadline).count())
                .isEqualTo(1);
        assertThat(result.completionRate()).isEqualTo(3 / 4.0);
        assertThat(result.deadlineComplianceRate()).isEqualTo(1 / 4.0);
        assertThat(result.internalScore()).isNull();
        assertThat(result.activityTypeSummary()).isEmpty();
        assertThat(result.competencyEvidence()).isEmpty();
    }

    @Test
    void 첨부가_있으면_건수_요약문을_만든다() {
        provider = newProvider();
        List<Task> tasks = List.of(
                taskOf(1L, "업무카드", TaskCategory.DEVELOP, TaskStatus.DONE,
                        LocalDate.of(2026, 6, 10), LocalDateTime.of(2026, 6, 9, 0, 0))
        );
        when(taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(PROJECT_MEMBER_ID))
                .thenReturn(tasks);
        when(taskAttachmentRepository.countByTaskIds(ArgumentMatchers.anyList()))
                .thenReturn(List.of(attachmentCount(1L, 3)));

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        assertThat(result.deliverableAttachmentInfo()).containsExactly("산출물 3건 첨부");
    }

    @Test
    void 첨부가_없으면_빈_리스트를_돌려준다() {
        provider = newProvider();
        List<Task> tasks = List.of(
                taskOf(1L, "업무카드", TaskCategory.DEVELOP, TaskStatus.TODO,
                        LocalDate.of(2026, 6, 10), null)
        );
        when(taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(PROJECT_MEMBER_ID))
                .thenReturn(tasks);
        when(taskAttachmentRepository.countByTaskIds(ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        assertThat(result.deliverableAttachmentInfo()).isEmpty();
    }

    private TaskAttachmentRepository.TaskAttachmentCount attachmentCount(Long taskId, long count) {
        return new TaskAttachmentRepository.TaskAttachmentCount() {
            @Override
            public Long getTaskId() {
                return taskId;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}