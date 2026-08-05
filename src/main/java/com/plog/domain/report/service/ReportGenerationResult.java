package com.plog.domain.report.service;

/**
 * 리포트 1건 생성 결과.
 *
 * @param reportId       대상 리포트
 * @param memberCount    대상 멤버 수
 * @param textSucceeded  LLM 텍스트까지 성공한 멤버 수. memberCount 보다 작으면 일부는 점수만 있다
 * @param published      COMPLETED 로 발행됐는지. false 면 FAILED 다
 */
public record ReportGenerationResult(
        Long reportId,
        int memberCount,
        int textSucceeded,
        boolean published
) {
    public boolean isPartial() {
        return published && textSucceeded < memberCount;
    }
}
