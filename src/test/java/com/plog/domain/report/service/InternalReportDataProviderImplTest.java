package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.TaskSummary;
import com.plog.domain.report.repository.ReportActivityLogRepository;
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
    @Mock private ReportActivityLogRepository activityLogRepository;

    private InternalReportDataProviderImpl provider;

    private InternalReportDataProviderImpl newProvider() {
        return new InternalReportDataProviderImpl(taskRepository, taskAttachmentRepository, activityLogRepository);
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

    private ProjectMember member() {
        return ProjectMember.builder().id(PROJECT_MEMBER_ID).build();
    }

    /** classify()는 noiseFiltered=false로 확정된 행에만 호출 가능하므로 매번 함께 적용한다. */
    private ReportActivityLog classifiedLog(
            RawActivityType rawType, SourceDomain domain, String content,
            LocalDateTime occurredAt, ActivityCategory category
    ) {
        ReportActivityLog log = ReportActivityLog.create(
                member(), domain, rawType, content, occurredAt, null, null);
        log.applyNoiseFilter(false);
        if (category != null) {
            log.classify(category);
        }
        return log;
    }

    private void stubNoTasks() {
        when(taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(PROJECT_MEMBER_ID))
                .thenReturn(List.of());
    }

    @Test
    void 업무카드와_활동_로그가_모두_없으면_예외_대신_empty를_돌려준다() {
        provider = newProvider();
        stubNoTasks();
        when(activityLogRepository.findByProjectMember_Id(PROJECT_MEMBER_ID)).thenReturn(List.of());

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        assertThat(result).isEqualTo(InternalReportData.empty());
    }

    @Test
    void 업무_집계와_기한내_완료_건수를_계산한다() {
        provider = newProvider();
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
        assertThat(result.taskCardSummary().stream().filter(TaskSummary::metDeadline).count())
                .isEqualTo(1);
        assertThat(result.completionRate()).isEqualTo(3 / 4.0);
        assertThat(result.deadlineComplianceRate()).isEqualTo(1 / 4.0);
        assertThat(result.internalScore()).isNull();
        assertThat(result.activityTypeSummary()).isEmpty();
        assertThat(result.competencyEvidence()).isEmpty();
    }

    @Test
    void 업무카드는_없지만_활동_로그만_있어도_예외_없이_결과를_돌려준다() {
        provider = newProvider();
        stubNoTasks();
        ReportActivityLog log = classifiedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "일정 다시 잡을게요",
                LocalDateTime.of(2026, 6, 5, 10, 0), ActivityCategory.SCHEDULE_COORDINATION);
        when(activityLogRepository.findByProjectMember_Id(PROJECT_MEMBER_ID)).thenReturn(List.of(log));

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        // 업무카드가 없어 0으로 나누는 상황이라도 NaN이 아니라 0.0으로 방어돼야 한다.
        assertThat(result.totalTaskCount()).isZero();
        assertThat(result.completionRate()).isZero();
        assertThat(result.deadlineComplianceRate()).isZero();
        assertThat(result.activityTypeSummary()).containsEntry(ActivityCategory.SCHEDULE_COORDINATION, 1);
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

    @Test
    void 활동_유형별_건수를_집계하고_미분류_활동은_제외한다() {
        provider = newProvider();
        stubNoTasks();
        List<ReportActivityLog> logs = List.of(
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "확정하겠습니다",
                        LocalDateTime.of(2026, 6, 1, 9, 0), ActivityCategory.DECISION),
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "이슈 해결했습니다",
                        LocalDateTime.of(2026, 6, 2, 9, 0), ActivityCategory.PROBLEM_SOLVING),
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "확정했습니다2",
                        LocalDateTime.of(2026, 6, 3, 9, 0), ActivityCategory.DECISION),
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "아직 분류 전",
                        LocalDateTime.of(2026, 6, 4, 9, 0), null) // 2단계 미처리 — 집계 제외
        );
        when(activityLogRepository.findByProjectMember_Id(PROJECT_MEMBER_ID)).thenReturn(logs);

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        assertThat(result.activityTypeSummary())
                .containsEntry(ActivityCategory.DECISION, 2)
                .containsEntry(ActivityCategory.PROBLEM_SOLVING, 1)
                .doesNotContainKey(ActivityCategory.SIMPLE_RESPONSE);
    }

    @Test
    void 역량별_근거는_최신순으로_최대_3개까지만_담는다() {
        provider = newProvider();
        stubNoTasks();
        List<ReportActivityLog> logs = List.of(
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "첫번째 피드백",
                        LocalDateTime.of(2026, 6, 1, 9, 0), ActivityCategory.FEEDBACK),
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "두번째 피드백",
                        LocalDateTime.of(2026, 6, 2, 9, 0), ActivityCategory.FEEDBACK),
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "세번째 피드백",
                        LocalDateTime.of(2026, 6, 3, 9, 0), ActivityCategory.FEEDBACK),
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "네번째(가장 최신) 피드백",
                        LocalDateTime.of(2026, 6, 4, 9, 0), ActivityCategory.FEEDBACK)
        );
        when(activityLogRepository.findByProjectMember_Id(PROJECT_MEMBER_ID)).thenReturn(logs);

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        List<String> evidence = result.competencyEvidence().get(CompetencyCategory.COMMUNICATION);
        assertThat(evidence).hasSize(3);
        assertThat(evidence.get(0)).contains("네번째(가장 최신) 피드백");
        assertThat(evidence.get(1)).contains("세번째 피드백");
        assertThat(evidence.get(2)).contains("두번째 피드백");
    }

    @Test
    void TASK_STATUS_CHANGE는_활동_유형_집계에는_포함되지만_근거_후보에서는_제외된다() {
        provider = newProvider();
        stubNoTasks();
        List<ReportActivityLog> logs = List.of(
                classifiedLog(RawActivityType.TASK_STATUS_CHANGE, SourceDomain.TASK, null,
                        LocalDateTime.of(2026, 6, 5, 9, 0), ActivityCategory.DELIVERABLE_SUBMIT),
                classifiedLog(RawActivityType.POST_CREATE, SourceDomain.POST, "산출물 공유드려요",
                        LocalDateTime.of(2026, 6, 1, 9, 0), ActivityCategory.DELIVERABLE_SUBMIT)
        );
        when(activityLogRepository.findByProjectMember_Id(PROJECT_MEMBER_ID)).thenReturn(logs);

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        assertThat(result.activityTypeSummary()).containsEntry(ActivityCategory.DELIVERABLE_SUBMIT, 2);
        List<String> evidence = result.competencyEvidence().get(CompetencyCategory.OUTPUT);
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0)).contains("산출물 공유드려요");
    }

    @Test
    void SIMPLE_RESPONSE는_어떤_역량_근거에도_포함되지_않는다() {
        provider = newProvider();
        stubNoTasks();
        List<ReportActivityLog> logs = List.of(
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "네 확인했습니다",
                        LocalDateTime.of(2026, 6, 1, 9, 0), ActivityCategory.SIMPLE_RESPONSE)
        );
        when(activityLogRepository.findByProjectMember_Id(PROJECT_MEMBER_ID)).thenReturn(logs);

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        assertThat(result.activityTypeSummary()).containsEntry(ActivityCategory.SIMPLE_RESPONSE, 1);
        assertThat(result.competencyEvidence().values().stream().flatMap(List::stream)).isEmpty();
    }

    @Test
    void 원문이_30자보다_길면_잘라낸다() {
        provider = newProvider();
        stubNoTasks();
        String longContent = "가".repeat(40);
        List<ReportActivityLog> logs = List.of(
                classifiedLog(RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, longContent,
                        LocalDateTime.of(2026, 6, 1, 9, 0), ActivityCategory.FEEDBACK)
        );
        when(activityLogRepository.findByProjectMember_Id(PROJECT_MEMBER_ID)).thenReturn(logs);

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        String evidenceLine = result.competencyEvidence().get(CompetencyCategory.COMMUNICATION).get(0);
        assertThat(evidenceLine).contains("가".repeat(30));
        assertThat(evidenceLine).doesNotContain("가".repeat(31));
    }

    @Test
    void content가_없는_업무카드_첨부는_고정_문구로_근거를_만든다() {
        provider = newProvider();
        stubNoTasks();
        List<ReportActivityLog> logs = List.of(
                classifiedLog(RawActivityType.TASK_ATTACHMENT_ADD, SourceDomain.TASK, null,
                        LocalDateTime.of(2026, 6, 1, 9, 0), ActivityCategory.DELIVERABLE_SUBMIT)
        );
        when(activityLogRepository.findByProjectMember_Id(PROJECT_MEMBER_ID)).thenReturn(logs);

        InternalReportData result = provider.provide(PROJECT_ID, PROJECT_MEMBER_ID);

        List<String> evidence = result.competencyEvidence().get(CompetencyCategory.OUTPUT);
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0)).isEqualTo("업무카드: 산출물 첨부 (6/1)");
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