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
 * @param completionRate             완료율 0.0~1.0
 * @param deadlineComplianceRate     마감 준수율 0.0~1.0
 * @param deliverableAttachmentInfo  산출물 첨부 요약 문자열. 파일명 등 식별정보는 제외할 것
 * @param activityTypeSummary        2단계 분류 유형별 건수. 분류되지 않은 유형은 키를 생략해도 된다
 * @param competencyEvidence         역량축 근거. 상완 쪽 외부 활동 근거와 병합되어 LLM 에 전달된다
 * @param internalScore              4-내부 점수 0~100, scale 2. {@link InternalReportDataProvider}
 *                                   구현체는 항상 값을 채워야 한다 — null이면
 *                                   {@code ReportMemberScoreService}에서 예외가 난다. 활동이 전혀
 *                                   없는 멤버는 {@link #empty()}를 통해 0으로 채워진다
 */
public record InternalReportData(
        List<TaskSummary> taskCardSummary,
        int totalTaskCount,
        int completedTaskCount,
        double completionRate,
        double deadlineComplianceRate,
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
     * internalScore는 null이 아니라 0.00 — {@code ReportMemberScoreService}가 internalScore를
     * 필수값으로 요구하므로, "계산할 데이터가 없다"는 곧 "내부 점수 0"으로 취급한다.
     */
    public static InternalReportData empty() {
        return new InternalReportData(
                List.of(), 0, 0, 0.0, 0.0, List.of(), Map.of(), Map.of(), new BigDecimal("0.00"));
    }
}