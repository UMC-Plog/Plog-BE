package com.plog.domain.report.entity;

/**
 * 리포트가 사람을 서술하는 4개 역량 축. 화면(팀 리포트의 팀원 카드, 개인 리포트의 기여도 상세)에
 * 그대로 대응한다.
 * <p>
 * 주의: 이 축은 {@code ReportMemberResult} 의 internal/external/peer/self 점수와 <b>다른 축</b>이다.
 * 그쪽은 "어느 데이터 소스에서 나온 점수인가"(소스축)이고, 이쪽은 "어떤 역량인가"(역량축)다.
 * 서로 파생되지 않으므로 한쪽에서 다른 쪽을 계산해 낼 수 없다.
 * <p>
 * {@link #LEADERSHIP} 는 Peer 평가의 {@code initiativeScore}(주도성) 컬럼에 대응한다 —
 * 저장 컬럼명과 화면 라벨("리더십")이 다르므로 매핑할 때 주의할 것.
 */
public enum CompetencyCategory {

    /** 협업 태도 — PeerEvaluation.collaborationScore */
    COLLABORATION,

    /** 리더십 — PeerEvaluation.initiativeScore (컬럼명은 주도성) */
    LEADERSHIP,

    /** 커뮤니케이션 — PeerEvaluation.communicationScore */
    COMMUNICATION,

    /** 산출물 기여 — PeerEvaluation.outputScore */
    OUTPUT
}
