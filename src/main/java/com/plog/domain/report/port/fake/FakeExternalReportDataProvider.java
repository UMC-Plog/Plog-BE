package com.plog.domain.report.port.fake;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.port.ExternalReportData;
import com.plog.domain.report.port.ExternalReportDataProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 상완 구현 전 임시 더미. 빈 등록은 {@link ReportPortFallbackConfig} 가 조건부로 한다 —
 * 이 클래스에 직접 {@code @Component} 를 붙이면 안 된다.
 */
@Slf4j
public class FakeExternalReportDataProvider implements ExternalReportDataProvider {

    @Override
    public ExternalReportData provide(Long projectId, Long projectMemberId) {
        log.warn("FakeExternalReportDataProvider 사용 중 — 실제 외부 연동 집계가 아닙니다. "
                + "projectId={}, projectMemberId={}", projectId, projectMemberId);
        // 짝수 멤버는 미연동으로 흘려서 가중치 비례 재분배 경로와 cautionText 프롬프트 분기까지
        // 로컬에서 함께 검증되게 한다.
        if (projectMemberId != null && projectMemberId % 2 == 0) {
            return ExternalReportData.notConnected();
        }
        return new ExternalReportData(
                true,
                Map.of(
                        SourceDomain.GITHUB, 24L,
                        SourceDomain.FIGMA, 5L,
                        SourceDomain.GOOGLE, 11L
                ),
                Map.of(
                        CompetencyCategory.OUTPUT,
                        List.of("GitHub: PR #42 병합 (6/5)", "GitHub: 커밋 24건"),
                        CompetencyCategory.COMMUNICATION,
                        List.of("GitHub: PR 리뷰 코멘트 9건")
                ),
                new BigDecimal("74.00"),
                ReliabilityTier.P2,
                "Notion이 연동되지 않아 일부 작업 과정은 반영되지 않았을 수 있습니다."
        );
    }
}
