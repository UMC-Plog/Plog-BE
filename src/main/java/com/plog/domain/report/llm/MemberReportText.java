package com.plog.domain.report.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 멤버 1명분 LLM 출력. 팀 리포트의 멤버 카드와 개인 리포트가 같은 입력으로 만들어지므로
 * 호출을 한 번으로 묶고 결과를 여기서 나눠 쓴다.
 *
 * <pre>
 *   headline           → 개인 리포트 상단 "AI 한줄 평가"
 *   teamMemberHeadline → 팀 리포트 멤버 카드 "AI 한줄 평가" (2줄)
 *   strengths   → 개인 리포트 ② 강점 분석 (카드 3개)
 *   weakness    → 개인 리포트 ③ 취약점 진단
 *   growth      → 개인 리포트 ④ AI 개인 성장 인사이트
 *   writing     → 개인 리포트 ⑤ AI 문장 변환
 * </pre>
 *
 * 최신 프롬프트는 모든 섹션을 출력하며, 근거가 부족한 문자열에는 고정 안내 문구를 넣는다.
 * nullable 처리는 과거 저장 응답과 스키마 미지원 Stub의 호환성을 위해 유지한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemberReportText(
        String headline,
        String teamMemberHeadline,
        List<StrengthCard> strengths,
        Weakness weakness,
        GrowthInsight growth,
        WritingSuggestion writing
) {
    public MemberReportText {
        // 과거 저장 응답이나 스키마 미지원 Stub도 읽을 수 있게 하되, 최신 프롬프트는 두 문장을 따로 생성한다.
        teamMemberHeadline = teamMemberHeadline == null || teamMemberHeadline.isBlank()
                ? headline : teamMemberHeadline;
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
    }

    public MemberReportText(
            String headline,
            List<StrengthCard> strengths,
            Weakness weakness,
            GrowthInsight growth,
            WritingSuggestion writing
    ) {
        this(headline, headline, strengths, weakness, growth, writing);
    }

    /** 개인 리포트 ② 강점 카드. 시안은 3개 고정이다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StrengthCard(String title, String description) {
    }

    /**
     * 개인 리포트 ③ 취약점 진단.
     *
     * @param title       주요 취약점 한 줄
     * @param suggestions 개선 제안 불릿. 시안은 3개다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Weakness(String title, List<String> suggestions) {
        public Weakness {
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        }
    }

    /** 개인 리포트 ④ 성장 포인트 / 유지 강점 / 다음 액션. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GrowthInsight(String growthPoint, String keepStrength, String nextAction) {
    }

    /** 개인 리포트 ⑤ 자기소개서 / 포트폴리오 추천 문장. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WritingSuggestion(String coverLetter, String portfolio) {
    }
}
