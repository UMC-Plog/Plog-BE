package com.plog.domain.report.port;

import java.util.Collection;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * 담당: 상완 (외부 연동 집계 · 신뢰도 등급 · 분석 한계 문구).
 * <p>
 * 리포트 생성 1회마다 프로젝트 멤버 전체를 한 번에 집계한다.
 */
public interface ExternalReportDataProvider {

    /**
     * 멤버별 외부 연동 활동 집계를 돌려준다.
     * <p>
     * 계약: 반환 맵은 요청한 모든 projectMemberId 를 키로 포함한다. 프로젝트가 외부 도구를
     * 하나도 연동하지 않았거나 이 멤버의 외부 계정 매핑이 없으면 예외 대신 정상 상태 DTO를
     * 돌려준다. provider 조회 자체가 실패하면 예외를 던지고 리포트 생성을 FAILED 로 전환한다.
     *
     * @return null 이 아닌 집계 결과
     */
    Map<Long, ExternalReportData> provide(Long projectId, Collection<Long> projectMemberIds);

    default Map<Long, ExternalReportData> provide(
            Long projectId, Collection<Long> projectMemberIds, LocalDateTime snapshotAt) {
        return provide(projectId, projectMemberIds);
    }
}
