package com.plog.domain.report.port;

/**
 * 담당: 상완 (외부 연동 집계 · 신뢰도 등급 · 분석 한계 문구).
 * <p>
 * 구현체를 {@code @Component} 로 등록하면 {@code FakeExternalReportDataProvider} 를 밀어내고
 * 자동으로 사용된다.
 */
public interface ExternalReportDataProvider {

    /**
     * 멤버 1명의 외부 연동 활동 집계를 돌려준다.
     * <p>
     * 계약: 프로젝트가 외부 도구를 하나도 연동하지 않았거나 이 멤버의 외부 계정 매핑이 없으면
     * 예외 대신 {@link ExternalReportData#notConnected()} 를 돌려줄 것.
     * 미연동은 오류가 아니라 정상 경로이며, 점수 가중치가 비례 재분배되는 것으로 처리된다.
     *
     * @return null 이 아닌 집계 결과
     */
    ExternalReportData provide(Long projectId, Long projectMemberId);
}
