package com.plog.domain.report.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportPromptLoaderTest {

    private final ReportPromptLoader loader = new ReportPromptLoader();

    @Test
    void 개인_프롬프트는_최신_계산값과_근거_부족_규칙을_명시한다() {
        String prompt = loader.memberSystemPrompt();

        assertThat(prompt)
                .contains("collaborationStability")
                .contains("vulnerableCompetency")
                .contains("개선 필요도")
                .contains("teamMemberHeadline")
                .contains("별도 문장으로 작성하세요")
                .contains("근거가 부족하면 strengths 자체를 출력하지 마세요")
                .contains("원문을 추정하거나 복원하지 마세요");
    }

    @Test
    void 팀_프롬프트는_백분율_스케일과_null_처리를_명시한다() {
        String prompt = loader.teamSystemPrompt();

        assertThat(prompt)
                .contains("서버가 확정한 0~100 값")
                .contains("팀의 협업 방식 자체")
                .contains("개인 headline에 쓸 문체를 사용하지 마세요")
                .contains("누락된 값을 0으로 간주하지 마세요")
                .contains("null 이면 해당 지표를 평가하거나 암시하지 마세요");
    }
}
