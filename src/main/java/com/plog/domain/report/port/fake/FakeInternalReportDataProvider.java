package com.plog.domain.report.port.fake;

import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.InternalReportDataProvider;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.report.port.TaskSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 송민 구현 전 임시 더미. 빈 등록은 {@link ReportPortFallbackConfig} 가 조건부로 한다 —
 * 이 클래스에 직접 {@code @Component} 를 붙이면 안 된다(그러면 실제 구현과 함께 등록돼 충돌한다).
 */
@Slf4j
public class FakeInternalReportDataProvider implements InternalReportDataProvider {

    @Override
    public InternalReportData provide(Long projectId, Long projectMemberId) {
        log.warn("FakeInternalReportDataProvider 사용 중 — 실제 내부 활동 집계가 아닙니다. "
                + "projectId={}, projectMemberId={}", projectId, projectMemberId);
        int seed = (int) (projectMemberId == null ? 0 : projectMemberId % 3);
        int total = 4;
        int completed = 4 - seed;
        return new InternalReportData(
                List.of(
                        new TaskSummary("로그인 API 구현", TaskCategory.DEVELOP,
                                TaskStatus.DONE, LocalDate.of(2026, 6, 5), true),
                        new TaskSummary("리포트 화면 기획", TaskCategory.PLANNING,
                                TaskStatus.DONE, LocalDate.of(2026, 6, 8), true),
                        new TaskSummary("배포 파이프라인 점검", TaskCategory.ETC,
                                TaskStatus.IN_PROGRESS, LocalDate.of(2026, 6, 12), false)
                ),
                total,
                completed,
                completed / (double) total,
                0.75 + seed * 0.05,
                List.of("발표자료 1건 첨부", "회의록 2건 첨부"),
                Map.of(
                        ActivityCategory.DECISION, 5,
                        ActivityCategory.PROBLEM_SOLVING, 8,
                        ActivityCategory.FEEDBACK, 6,
                        ActivityCategory.DELIVERABLE_SUBMIT, 3,
                        ActivityCategory.SCHEDULE_COORDINATION, 4
                ),
                Map.of(
                        CompetencyCategory.COLLABORATION,
                        List.of("채팅: 일정 재조정 제안 (6/2)", "게시글: 회고 정리 공유 (6/10)"),
                        CompetencyCategory.LEADERSHIP,
                        List.of("채팅: 우선순위 기준 정리 (5/28)"),
                        CompetencyCategory.COMMUNICATION,
                        List.of("댓글: 리뷰 피드백 반영 확인 (6/4)"),
                        CompetencyCategory.OUTPUT,
                        List.of("업무카드: 로그인 API 완료 (6/5)")
                ),
                new BigDecimal("88.00")
        );
    }
}
