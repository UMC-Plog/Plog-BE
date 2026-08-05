package com.plog.domain.report.port;

import com.plog.domain.report.entity.CompetencyCategory;
import java.util.List;

/**
 * 역량 1개에 대한 "점수 + 그 점수의 근거". LLM 은 이 묶음을 받아 <b>서술만</b> 하고
 * 점수를 다시 계산하거나 바꾸지 않는다.
 *
 * @param category       역량 축
 * @param score          0~100 정규화 점수. 아직 계산 전이면 null — 이 경우 LLM 은 해당 역량을 언급하지 않는다
 * @param activityCount  이 역량으로 분류된 활동 건수
 * @param evidence       대표 근거 문자열. <b>원본 로그가 아니라 이미 요약된 한 줄</b>이어야 한다
 *                       (예: "채팅: 로그인 에러 원인 규명 (8/5)"). 근거가 없으면 빈 리스트
 */
public record CompetencyEvidence(
        CompetencyCategory category,
        Double score,
        int activityCount,
        List<String> evidence
) {
    public CompetencyEvidence {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
