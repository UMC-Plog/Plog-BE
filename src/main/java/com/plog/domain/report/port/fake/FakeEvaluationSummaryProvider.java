package com.plog.domain.report.port.fake;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.port.EvaluationSummaryProvider;
import com.plog.domain.report.port.PeerEvaluationSummary;
import com.plog.domain.report.port.SelfFeedbackMatchSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 서희 구현 전 임시 더미. 빈 등록은 {@link ReportPortFallbackConfig} 가 조건부로 한다 —
 * 이 클래스에 직접 {@code @Component} 를 붙이면 안 된다.
 */
@Slf4j
public class FakeEvaluationSummaryProvider implements EvaluationSummaryProvider {

    @Override
    public PeerEvaluationSummary peer(Long projectId, Long projectMemberId) {
        log.warn("FakeEvaluationSummaryProvider.peer 사용 중 — 실제 Peer 집계가 아닙니다. "
                + "projectId={}, projectMemberId={}", projectId, projectMemberId);
        // 5점 척도(화면 표기)와 100점 척도(점수 조립)를 함께 채워 두 축이 섞이는 실수를 로컬에서 잡는다.
        return new PeerEvaluationSummary(
                new BigDecimal("4.25"),
                Map.of(
                        CompetencyCategory.COLLABORATION, new BigDecimal("4.4"),
                        CompetencyCategory.LEADERSHIP, new BigDecimal("4.2"),
                        CompetencyCategory.COMMUNICATION, new BigDecimal("4.0"),
                        CompetencyCategory.OUTPUT, new BigDecimal("4.4")
                ),
                3,
                List.of("리더십", "책임감"),
                new BigDecimal("80.00")
        );
    }

    @Override
    public SelfFeedbackMatchSummary self(Long projectId, Long projectMemberId) {
        log.warn("FakeEvaluationSummaryProvider.self 사용 중 — 실제 자기 피드백 집계가 아닙니다. "
                + "projectId={}, projectMemberId={}", projectId, projectMemberId);
        // 3의 배수 멤버는 미제출로 흘려서 "자기 인식 서술 생략" 프롬프트 분기를 로컬에서 검증한다.
        if (projectMemberId != null && projectMemberId % 3 == 0) {
            return SelfFeedbackMatchSummary.notSubmitted();
        }
        return new SelfFeedbackMatchSummary(
                true,
                7,
                2,
                1,
                new BigDecimal("0.70"),
                new BigDecimal("70.00")
        );
    }
}
