package com.plog.domain.report.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 팀 리포트 상단 "AI 인사이트". 프로젝트 1건당 한 번 생성한다.
 * <p>
 * 시안은 두 문단이다 — 잘한 점 한 문단, 개선 제안 한 문단. 화면에서 5~7줄로 보이므로
 * 두 필드를 합쳐 180자 안쪽을 목표로 한다.
 *
 * @param strength   팀이 잘한 협업 흐름
 * @param suggestion 다음 프로젝트를 위한 제안
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamReportText(String strength, String suggestion) {

    public boolean isEmpty() {
        return (strength == null || strength.isBlank())
                && (suggestion == null || suggestion.isBlank());
    }
}
