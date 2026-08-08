package com.plog.domain.report.service;

import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.InternalReportDataProvider;
import com.plog.domain.report.port.TaskSummary;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskOverdueCalculator;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link InternalReportDataProvider} 실제 구현 (0~4단계 중 0~1단계: 업무카드 집계).
 * <p>
 * 2단계 활동 유형 분류({@code activityTypeSummary})와 4단계 역량 근거 추출
 * ({@code competencyEvidence})는 아직 구현하지 않아 빈 Map 을 돌려준다. 내부 점수
 * ({@code internalScore})도 스코어링 단계가 없어 null 이다. 세 필드 모두 다음 이슈에서 채운다.
 * <p>
 * 활동(업무카드)이 하나도 없는 멤버는 예외 대신 {@link InternalReportData#empty()} 를 돌려준다 —
 * 참여가 저조한 멤버도 리포트에 나와야 한다는 포트 계약을 지키기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class InternalReportDataProviderImpl implements InternalReportDataProvider {

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;

    @Override
    @Transactional(readOnly = true)
    public InternalReportData provide(Long projectId, Long projectMemberId) {
        List<Task> tasks = taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(projectMemberId);
        if (tasks.isEmpty()) {
            return InternalReportData.empty();
        }

        List<TaskSummary> taskCardSummary = tasks.stream()
                .map(this::toTaskSummary)
                .toList();

        int totalTaskCount = tasks.size();
        int completedTaskCount = (int) tasks.stream()
                .filter(task -> task.getCardStatus() == TaskStatus.DONE)
                .count();
        int deadlineMetTaskCount = (int) taskCardSummary.stream()
                .filter(TaskSummary::metDeadline)
                .count();

        double completionRate = completedTaskCount / (double) totalTaskCount;
        double deadlineComplianceRate = deadlineMetTaskCount / (double) totalTaskCount;

        return new InternalReportData(
                taskCardSummary,
                totalTaskCount,
                completedTaskCount,
                completionRate,
                deadlineComplianceRate,
                summarizeAttachments(tasks),
                Map.<ActivityCategory, Integer>of(),
                Map.<CompetencyCategory, List<String>>of(),
                null
        );
    }

    // 완료되지 않았거나 마감일이 없으면 metDeadline=false (포트 계약).
    // "완료" + "마감일 이후에 완료"가 아님을 기존 TaskOverdueCalculator 로 판정한다
    // (지연 완료 판정 로직을 여기서 새로 만들지 않고 재사용 — 원본 로직과 어긋날 위험을 없앤다).
    private TaskSummary toTaskSummary(Task task) {
        boolean metDeadline = task.getCardStatus() == TaskStatus.DONE
                && task.getEndDate() != null
                && !TaskOverdueCalculator.isOverdue(task);
        return new TaskSummary(
                task.getTitle(),
                task.getCategory(),
                task.getCardStatus(),
                task.getEndDate(),
                metDeadline
        );
    }

    /**
     * 산출물 첨부 요약. 지금은 "총 N건 첨부" 형태의 단순 합계만 만든다.
     * <p>
     * 판단 근거: 업무별/카테고리별로 나눠 보여주려면 2단계 활동 유형 분류가 먼저 있어야
     * 의미가 생긴다(분류 없이 나누면 임의 기준이 된다). 그 전까지는 파일명 등 식별정보를
     * 넣지 않는다는 계약만 지키는 최소 형태로 두고, 분류 이슈에서 함께 고도화한다.
     */
    private List<String> summarizeAttachments(List<Task> tasks) {
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        long totalAttachments = taskAttachmentRepository.countByTaskIds(taskIds).stream()
                .mapToLong(TaskAttachmentRepository.TaskAttachmentCount::getCount)
                .sum();
        return totalAttachments == 0 ? List.of() : List.of("산출물 " + totalAttachments + "건 첨부");
    }
}