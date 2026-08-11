package com.plog.domain.report.llm;

import com.plog.domain.project.entity.ProjectType;
import java.math.BigDecimal;
import java.util.List;

/**
 * 팀 인사이트 생성용 입력. 프로젝트 1건당 한 번 만든다.
 * <p>
 * 멤버 입력과 마찬가지로 실명·프로젝트명을 담지 않는다. 멤버별 값은 이름 없이 분포로만 넘겨서,
 * LLM 이 개인을 지목하는 문장을 쓸 수 없게 한다.
 *
 * @param memberFinalScores 멤버별 최종 점수(이름 없음). 편차를 보고 "고르게 기여" 같은 서술을 하도록
 * @param memberHeadlines   팀 카드용 멤버별 활동 요약(이름 없음). 개인 리포트 headline은 넣지 않는다
 */
public record TeamLlmInput(
        ProjectType projectType,
        int teamSize,
        Double teamCompletionRate,
        Double teamDeadlineComplianceRate,
        List<BigDecimal> memberFinalScores,
        List<String> memberHeadlines,
        boolean externalToolConnected
) {
    public TeamLlmInput {
        memberFinalScores = memberFinalScores == null ? List.of() : List.copyOf(memberFinalScores);
        memberHeadlines = memberHeadlines == null ? List.of() : List.copyOf(memberHeadlines);
    }
}
