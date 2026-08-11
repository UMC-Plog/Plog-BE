package com.plog.domain.report.port;

import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.CompetencyCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 송민 담당 구간(0~4단계 내부 도메인)의 산출물. 멤버 1명분이다.
 *
 * @param taskCardSummary            담당 업무카드 요약
 * @param totalTaskCount             부여된 전체 업무 수 (팀 리포트 표의 "전체")
 * @param completedTaskCount         완료한 업무 수 (팀 리포트 표의 "완료")
 * @param deadlineMetTaskCount       마감일 안에 완료한 업무 수
 * @param deadlineTargetTaskCount    마감일이 있어 준수율 계산 대상인 업무 수
 * @param completionRate             완료율 0.0~1.0. 업무가 없으면 null
 * @param deadlineComplianceRate     마감 준수율 0.0~1.0. 마감 대상 업무가 없으면 null
 * @param deliverableAttachmentInfo  산출물 첨부 요약 문자열. 파일명 등 식별정보는 제외할 것
 * @param activityTypeSummary        2단계 분류 유형별 건수. 분류되지 않은 유형은 키를 생략해도 된다
 * @param competencyEvidence         역량축 근거. 상완 쪽 외부 활동 근거와 병합되어 LLM 에 전달된다
 * @param internalScore              4-내부 점수 0~100, scale 2. 계산 가능한 내부 축이 없으면 null
 */
public record InternalReportData(
        List<TaskSummary> taskCardSummary,
        int totalTaskCount,
        int completedTaskCount,
        int deadlineMetTaskCount,
        int deadlineTargetTaskCount,
        Double completionRate,
        Double deadlineComplianceRate,
        List<String> deliverableAttachmentInfo,
        Map<ActivityCategory, Integer> activityTypeSummary,
        Map<CompetencyCategory, List<String>> competencyEvidence,
        BigDecimal internalScore
) {
    public InternalReportData {
        taskCardSummary = taskCardSummary == null ? List.of() : List.copyOf(taskCardSummary);
        deliverableAttachmentInfo = deliverableAttachmentInfo == null
                ? List.of() : List.copyOf(deliverableAttachmentInfo);
        activityTypeSummary = activityTypeSummary == null ? Map.of() : Map.copyOf(activityTypeSummary);
        competencyEvidence = competencyEvidence == null ? Map.of() : Map.copyOf(competencyEvidence);
    }

    /**
     * 데이터가 아직 없는 멤버(업무카드·활동 로그 모두 0건)를 표현하는 빈 값.
     * 업무·활동이 모두 없으므로 비율과 internalScore는 실제 0이 아니라 측정 불가(null)다.
     */
    public static InternalReportData empty() {
        return new InternalReportData(
                List.of(), 0, 0, 0, 0, null, null, List.of(), Map.of(), Map.of(), null);
    }

    /** 기존 조립 코드 호환용. 마감 대상/준수 건수는 업무 요약에서 파생한다. */
    public InternalReportData(
            List<TaskSummary> taskCardSummary,
            int totalTaskCount,
            int completedTaskCount,
            Double completionRate,
            Double deadlineComplianceRate,
            List<String> deliverableAttachmentInfo,
            Map<ActivityCategory, Integer> activityTypeSummary,
            Map<CompetencyCategory, List<String>> competencyEvidence,
            BigDecimal internalScore
    ) {
        this(taskCardSummary, totalTaskCount, completedTaskCount,
                taskCardSummary == null ? 0 : (int) taskCardSummary.stream().filter(TaskSummary::metDeadline).count(),
                taskCardSummary == null ? 0 : (int) taskCardSummary.stream().filter(task -> task.deadline() != null).count(),
                completionRate, deadlineComplianceRate, deliverableAttachmentInfo,
                activityTypeSummary, competencyEvidence, internalScore);
    }
}
