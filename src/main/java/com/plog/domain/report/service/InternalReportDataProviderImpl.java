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
    private static final DateTimeFormatter EVIDENCE_DATE_FORMAT = DateTimeFormatter.ofPattern("M/d");

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ReportActivityLogRepository activityLogRepository;

    @Override
    @Transactional(readOnly = true)
    public InternalReportData provide(Long projectId, Long projectMemberId) {
        return provide(projectId, projectMemberId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public InternalReportData provide(Long projectId, Long projectMemberId, java.time.LocalDateTime snapshotAt) {
        List<Task> tasks = snapshotAt == null
                ? taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(projectMemberId)
                : taskRepository.findAllByProjectMember_IdAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
                        projectMemberId, snapshotAt);
        List<ReportActivityLog> activities = snapshotAt == null
                ? activityLogRepository.findByProjectMember_Id(projectMemberId)
                : activityLogRepository.findByProjectMember_IdAndOccurredAtLessThanEqual(projectMemberId, snapshotAt);

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
        int deadlineTargetTaskCount = (int) tasks.stream()
                .filter(task -> task.getEndDate() != null)
                .count();

        // totalTaskCount=0(업무카드는 없지만 채팅 등 활동만 있는 멤버)인 경우 0으로 나누면 NaN이
        // 되므로 방어. 이전에는 tasks.isEmpty()에서 이미 early return 했기 때문에 없던 케이스다.
        Double completionRate = totalTaskCount == 0 ? null : completedTaskCount / (double) totalTaskCount;
        Double deadlineComplianceRate = deadlineTargetTaskCount == 0
                ? null : deadlineMetTaskCount / (double) deadlineTargetTaskCount;

        Map<ActivityCategory, Integer> activityTypeSummary = summarizeActivityTypes(activities);
        BigDecimal internalScore = InternalScoreCalculator.calculate(
                activityTypeSummary, totalTaskCount, completionRate, deadlineComplianceRate);

        return new InternalReportData(
                taskCardSummary,
                totalTaskCount,
                completedTaskCount,
                deadlineMetTaskCount,
                deadlineTargetTaskCount,
                completionRate,
                deadlineComplianceRate,
                summarizeAttachments(tasks, snapshotAt),
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
    private List<String> summarizeAttachments(List<Task> tasks, java.time.LocalDateTime snapshotAt) {
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        var counts = snapshotAt == null
                ? taskAttachmentRepository.countByTaskIds(taskIds)
                : taskAttachmentRepository.countByTaskIdsAt(taskIds, snapshotAt);
        long totalAttachments = counts.stream()
                .mapToLong(TaskAttachmentRepository.TaskAttachmentCount::getCount)
                .sum();
        return totalAttachments == 0 ? List.of() : List.of("산출물 " + totalAttachments + "건 첨부");
    }

    /**
     * 카테고리별 분류된 활동 건수. classifiedType이 없는(2단계 미처리) 행은 집계에서 제외한다.
     * TASK_STATUS_CHANGE도 제외한다 — 상태 변경 로그는 감사·이력용으로 ReportActivityLog에
     * 남기지만 점수에는 반영하지 않는다: IN_PROGRESS 전환은 일정 조율의 증거가 아니고, 상태를
     * 여러 번 바꾸면 활동 점수가 부풀려질 수 있으며, 무엇보다 completionRate/deadlineComplianceRate에
     * 이미 업무 상태가 반영돼 있어 activityComponent에서 또 세면 이중 계산이 된다.
     */
    private Map<ActivityCategory, Integer> summarizeActivityTypes(List<ReportActivityLog> activities) {
        Map<ActivityCategory, Integer> summary = new EnumMap<>(ActivityCategory.class);
        for (ReportActivityLog activity : activities) {
            if (activity.getRawActivityType() == RawActivityType.TASK_STATUS_CHANGE) {
                continue;
            }
            ActivityCategory category = activity.getClassifiedType();
            if (category == null) {
                continue;
            }
            summary.merge(category, 1, Integer::sum);
        }
        return summary;
    }

    /**
     * 역량별 대표 근거 최대 3개. 분류 정보량, 업무 연결, 출처 검증 가능성과 출처/유형 다양성을
     * 우선하고 발생 시각은 동점 기준으로만 사용한다.
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
            List<String> lines = selectRepresentativeEvidence(entry.getValue()).stream()
                    .map(this::toEvidenceLine)
                    .toList();
            evidence.put(entry.getKey(), lines);
        }
        return evidence;
    }

    private List<ReportActivityLog> selectRepresentativeEvidence(List<ReportActivityLog> candidates) {
        List<ReportActivityLog> remaining = new ArrayList<>();
        java.util.Set<String> seenSourceRefs = new java.util.HashSet<>();
        for (ReportActivityLog candidate : candidates) {
            String ref = candidate.getSourceRefId();
            if (ref == null || ref.isBlank() || seenSourceRefs.add(candidate.getSourceDomain() + ":" + ref)) {
                remaining.add(candidate);
            }
        }

        List<ReportActivityLog> selected = new ArrayList<>();
        java.util.Set<com.plog.domain.report.entity.SourceDomain> usedDomains = new java.util.HashSet<>();
        java.util.Set<RawActivityType> usedTypes = new java.util.HashSet<>();
        while (!remaining.isEmpty() && selected.size() < MAX_EVIDENCE_PER_COMPETENCY) {
            ReportActivityLog best = remaining.stream()
                    .max(Comparator.comparingInt((ReportActivityLog activity) ->
                                    evidenceScore(activity, usedDomains, usedTypes))
                            .thenComparing(ReportActivityLog::getOccurredAt)
                            .thenComparing(activity -> activity.getSourceDomain().name(), Comparator.reverseOrder())
                            .thenComparing(activity -> activity.getRawActivityType().name(), Comparator.reverseOrder())
                            .thenComparing(activity -> java.util.Objects.toString(activity.getSourceRefId(), ""),
                                    Comparator.reverseOrder()))
                    .orElseThrow();
            selected.add(best);
            remaining.remove(best);
            usedDomains.add(best.getSourceDomain());
            usedTypes.add(best.getRawActivityType());
        }
        return selected;
    }

    private int evidenceScore(
            ReportActivityLog activity,
            java.util.Set<com.plog.domain.report.entity.SourceDomain> usedDomains,
            java.util.Set<RawActivityType> usedTypes
    ) {
        int score = switch (activity.getClassifiedType()) {
            case DECISION, PROBLEM_SOLVING -> 5;
            case DELIVERABLE_SUBMIT -> 4;
            case FEEDBACK, SCHEDULE_COORDINATION -> 3;
            case SIMPLE_RESPONSE -> 0;
        };
        if (activity.getLinkedTask() != null) {
            score += 3;
        }
        if (activity.getSourceRefId() != null && !activity.getSourceRefId().isBlank()) {
            score += 1;
        }
        if (!usedDomains.contains(activity.getSourceDomain())) {
            score += 2;
        }
        if (!usedTypes.contains(activity.getRawActivityType())) {
            score += 1;
        }
        return score;
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

    private String evidenceText(ReportActivityLog activity) {
        return switch (activity.getClassifiedType()) {
            case DECISION -> "업무 관련 의사결정을 공유함";
            case PROBLEM_SOLVING -> "업무 진행 중 문제 해결에 기여함";
            case FEEDBACK -> "업무 진행에 필요한 피드백을 제공함";
            case DELIVERABLE_SUBMIT -> activity.getRawActivityType() == RawActivityType.TASK_ATTACHMENT_ADD
                    ? "산출물을 제출함" : "프로젝트 관련 자료를 공유함";
            case SCHEDULE_COORDINATION -> "업무 일정과 진행 방향을 조율함";
            case SIMPLE_RESPONSE -> "업무 관련 의견을 공유함";
        };
    }
}
