package com.plog.domain.report.service;

/**
 * 리포트 자동 생성 배치 1회의 결과.
 *
 * @param scanned 대상으로 조회된 프로젝트 수
 * @param started 리포트를 새로 시작한 수
 * @param skipped 이미 리포트가 있어 건너뛴 수 (경합으로 다른 경로가 먼저 만든 경우)
 * @param failed  처리 중 예외가 나서 다음 회차로 미뤄진 수
 */
public record ReportBatchResult(int scanned, int started, int skipped, int failed) {

    public static ReportBatchResult empty() {
        return new ReportBatchResult(0, 0, 0, 0);
    }

    public boolean hasWork() {
        return scanned > 0;
    }
}
