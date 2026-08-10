package com.plog.domain.report.service;

import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.InternalReportDataProvider;
import com.plog.domain.report.port.TaskSummary;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskOverdueCalculator;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskRepository;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link InternalReportDataProvider} 실제 구현 (0~4단계: 업무카드 집계 + 활동 유형 요약/역량 근거
 * + 내부 점수 계산).
 * <p>
 * 내부 점수({@code internalScore}) 계산은 {@link InternalScoreCalculator}에 위임한다 — 가중치·정규화
 * 방식이 바뀌어도 이 클래스는 손댈 필요가 없도록 분리했다.
 * <p>
 * 활동(업무카드·활동 로그)이 하나도 없는 멤버는 예외 대신 {@link InternalReportData#empty()}를
 * 돌려준다 — 참여가 저조한 멤버도 리포트에 나와야 한다는 포트 계약을 지키기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class InternalReportDataProviderImpl implements InternalReportDataProvider {

    private static final int MAX_EVIDENCE_PER_COMPETENCY = 3;
    private static final int EVIDENCE_CONTENT_TRUNCATE_LENGTH = 30;
    private static final DateTimeFormatter EVIDENCE_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d");

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ReportActivityLogRepository activityLogRepository;

    @Override
    @Transactional(readOnly = true)
    public InternalReportData provide(Long projectId, Long projectMemberId) {
        List<Task> tasks = taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(projectMemberId);
        List<ReportActivityLog> activities = activityLogRepository.findByProjectMember_Id(projectMemberId);

        if (tasks.isEmpty() && activities.isEmpty()) {
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

        // totalTaskCount=0(업무카드는 없지만 채팅 등 활동만 있는 멤버)인 경우 0으로 나누면 NaN이
        // 되므로 방어. 이전에는 tasks.isEmpty()에서 이미 early return 했기 때문에 없던 케이스다.
        double completionRate = totalTaskCount == 0 ? 0.0 : completedTaskCount / (double) totalTaskCount;
        double deadlineComplianceRate = totalTaskCount == 0 ? 0.0 : deadlineMetTaskCount / (double) totalTaskCount;

        Map<ActivityCategory, Integer> activityTypeSummary = summarizeActivityTypes(activities);
        BigDecimal internalScore = InternalScoreCalculator.calculate(
                activityTypeSummary, totalTaskCount, completionRate, deadlineComplianceRate);

        return new InternalReportData(
                taskCardSummary,
                totalTaskCount,
                completedTaskCount,
                completionRate,
                deadlineComplianceRate,
                summarizeAttachments(tasks),
                activityTypeSummary,
                extractCompetencyEvidence(activities),
                internalScore
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
     * 의미가 생긴다(분류 없이 나누면 임의 기준이 된다). 2단계는 이번에 구현했지만, 이 요약은
     * TaskAttachment(업무카드 산출물)만 다루고 활동 로그(채팅/게시글) 산출물과는 성격이 달라
     * 고도화는 별도 이슈로 남긴다.
     */
    private List<String> summarizeAttachments(List<Task> tasks) {
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        long totalAttachments = taskAttachmentRepository.countByTaskIds(taskIds).stream()
                .mapToLong(TaskAttachmentRepository.TaskAttachmentCount::getCount)
                .sum();
        return totalAttachments == 0 ? List.of() : List.of("산출물 " + totalAttachments + "건 첨부");
    }

    /** 카테고리별 분류된 활동 건수. classifiedType이 없는(2단계 미처리) 행은 집계에서 제외한다. */
    private Map<ActivityCategory, Integer> summarizeActivityTypes(List<ReportActivityLog> activities) {
        Map<ActivityCategory, Integer> summary = new EnumMap<>(ActivityCategory.class);
        for (ReportActivityLog activity : activities) {
            ActivityCategory category = activity.getClassifiedType();
            if (category == null) {
                continue;
            }
            summary.merge(category, 1, Integer::sum);
        }
        return summary;
    }

    /**
     * 역량별 대표 근거 최대 3개, 최신순. TASK_STATUS_CHANGE는 "업무카드 상태가 바뀌었다"는
     * 사실만 있고 사람이 읽을 근거 문장으로 삼기엔 정보가 빈약해 근거 후보에서 제외한다
     * (activityTypeSummary 집계에는 포함되지만 근거로는 노출하지 않는다).
     */
    private Map<CompetencyCategory, List<String>> extractCompetencyEvidence(List<ReportActivityLog> activities) {
        Map<CompetencyCategory, List<ReportActivityLog>> byCompetency = new EnumMap<>(CompetencyCategory.class);
        for (ReportActivityLog activity : activities) {
            if (activity.getRawActivityType() == RawActivityType.TASK_STATUS_CHANGE) {
                continue;
            }
            ActivityCategory category = activity.getClassifiedType();
            if (category == null) {
                continue;
            }
            CompetencyCategory competency = category.competencyCategory();
            if (competency == null) {
                continue;
            }
            byCompetency.computeIfAbsent(competency, key -> new ArrayList<>()).add(activity);
        }

        Map<CompetencyCategory, List<String>> evidence = new EnumMap<>(CompetencyCategory.class);
        for (Map.Entry<CompetencyCategory, List<ReportActivityLog>> entry : byCompetency.entrySet()) {
            List<String> lines = entry.getValue().stream()
                    .sorted(Comparator.comparing(ReportActivityLog::getOccurredAt).reversed())
                    .limit(MAX_EVIDENCE_PER_COMPETENCY)
                    .map(this::toEvidenceLine)
                    .toList();
            evidence.put(entry.getKey(), lines);
        }
        return evidence;
    }

    private String toEvidenceLine(ReportActivityLog activity) {
        String label = sourceLabel(activity.getRawActivityType());
        String text = evidenceText(activity);
        String date = activity.getOccurredAt().format(EVIDENCE_DATE_FORMAT);
        return label + ": " + text + " (" + date + ")";
    }

    private String sourceLabel(RawActivityType rawActivityType) {
        return switch (rawActivityType) {
            case CHAT_MESSAGE -> "채팅";
            case POST_CREATE -> "게시글";
            case COMMENT_CREATE -> "댓글";
            case TASK_ATTACHMENT_ADD -> "업무카드";
            default -> throw new IllegalStateException("근거 추출 대상이 아닌 활동 유형입니다: " + rawActivityType);
        };
    }

    /**
     * 원문 30자 truncate. content가 없는 유형(TASK_ATTACHMENT_ADD)은 원문 자체가 없으므로
     * 고정 설명으로 대체한다 — 실측 없이 잡은 가정이라 리포트 확인 후 문구를 다듬을 수 있다.
     */
    private String evidenceText(ReportActivityLog activity) {
        String content = activity.getContent();
        if (content == null || content.isBlank()) {
            return "산출물 첨부";
        }
        return content.length() <= EVIDENCE_CONTENT_TRUNCATE_LENGTH
                ? content
                : content.substring(0, EVIDENCE_CONTENT_TRUNCATE_LENGTH);
    }
}