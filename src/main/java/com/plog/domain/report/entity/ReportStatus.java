package com.plog.domain.report.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 리포트 생명주기 상태. 전이는 {@link Report} 의 메서드 안에서만 일어나고
 * (외부에 setter 가 없다), 아래 표에 없는 전이는 IllegalStateException 이다.
 *
 * <pre>
 *   (없음) --Report.start(project)--> GENERATING
 *   GENERATING --complete(completedAt)--> COMPLETED   (종료)
 *   GENERATING --fail()-----------------> FAILED      (종료)
 *   COMPLETED  --attachPdf(key, name)---> COMPLETED   (상태 유지, PDF 메타데이터만 기록)
 * </pre>
 *
 * COMPLETED 가 곧 "발행 완료"다 — 별도의 PUBLISHED 상태를 두지 않는다.
 * 조회 API 와 PDF 다운로드가 열리는 유일한 상태이며, ReportPublishedEvent 도 이 전이에서 나간다.
 * <p>
 * COMPLETED / FAILED 는 종료 상태라 다시 전이할 수 없다. 실패한 리포트를 다시 만들려면
 * 같은 프로젝트에 Report 행을 새로 시작한다 — reports 는 project_id 유니크가 아니다.
 */
public enum ReportStatus {
    GENERATING,
    COMPLETED,
    FAILED;

    /** 더 이상 전이할 수 없는 종료 상태인지. */
    public boolean isTerminal() {
        return this != GENERATING;
    }

    /** 발행 완료 — 멤버 결과가 확정된 상태. PDF ZIP은 별도 저장소 실패 시 없을 수 있다. */
    public boolean isPublished() {
        return this == COMPLETED;
    }

    /**
     * 이 상태의 리포트가 이미 있으면 같은 프로젝트에 리포트를 새로 시작하지 않는다 — 생성 멱등성의 기준.
     * FAILED 만 빠져 있는 건 의도적이다: 실패한 리포트는 재시도 대상이어야 한다.
     */
    public static Set<ReportStatus> restartBlockingStatuses() {
        return EnumSet.of(GENERATING, COMPLETED);
    }
}
