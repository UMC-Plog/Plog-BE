package com.plog.domain.report.entity;

/**
 * 리포트 생명주기 상태. 전이는 {@link Report} 의 메서드 안에서만 일어나고
 * (외부에 setter 가 없다), 아래 표에 없는 전이는 IllegalStateException 이다.
 *
 * <pre>
 *   (없음) --Report.start(project)--> GENERATING
 *   GENERATING --complete(completedAt)--> COMPLETED   (종료)
 *   GENERATING --fail()-----------------> FAILED
 *   FAILED     --restart()--------------> GENERATING
 *   COMPLETED  --attachPdf(key, name)---> COMPLETED   (상태 유지, PDF 메타데이터만 기록)
 * </pre>
 *
 * COMPLETED 가 곧 "발행 완료"다 — 별도의 PUBLISHED 상태를 두지 않는다.
 * 조회 API 와 PDF 다운로드가 열리는 유일한 상태이며, ReportPublishedEvent 도 이 전이에서 나간다.
 * <p>
 * COMPLETED 는 종료 상태다. FAILED 는 자동 재큐잉 시 같은 Report 행에서 GENERATING 으로 돌아간다.
 * 프로젝트당 리포트는 한 건이며 사용자 재생성 요청으로 새 Report 행을 만들지는 않는다.
 */
public enum ReportStatus {
    GENERATING,
    COMPLETED,
    FAILED;

    /** 현재 생성 작업이 끝난 상태인지. FAILED 는 명시적인 restart 로만 다시 전이할 수 있다. */
    public boolean isTerminal() {
        return this != GENERATING;
    }

    /** 발행 완료 — 멤버 결과가 확정된 상태. PDF ZIP은 별도 저장소 실패 시 없을 수 있다. */
    public boolean isPublished() {
        return this == COMPLETED;
    }
}
