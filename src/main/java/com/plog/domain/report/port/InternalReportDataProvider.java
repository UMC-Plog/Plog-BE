package com.plog.domain.report.port;

/**
 * 담당: 송민 (0~4단계 내부 도메인 — 수집 · 정제 · 분류 · 연결 · 내부 점수).
 * <p>
 * 구현체를 {@code @Component} 로 등록하면 {@code FakeInternalReportDataProvider} 를 밀어내고
 * 자동으로 사용된다. 오케스트레이션 쪽은 수정할 필요가 없다.
 */
public interface InternalReportDataProvider {

    /**
     * 멤버 1명의 내부 활동 집계를 돌려준다.
     * <p>
     * 계약: 활동이 하나도 없는 멤버여도 예외를 던지지 말고 {@link InternalReportData#empty()} 를
     * 돌려줄 것. 리포트는 참여가 저조한 멤버도 포함해서 발행되어야 한다.
     * 여기서 예외가 나면 해당 멤버의 리포트 생성만 실패로 기록되고 나머지 멤버는 계속 진행된다.
     *
     * @return null 이 아닌 집계 결과
     */
    InternalReportData provide(Long projectId, Long projectMemberId);
}
