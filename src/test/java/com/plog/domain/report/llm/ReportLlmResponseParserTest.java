package com.plog.domain.report.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.infrastructure.ai.LlmGenerationException;
import com.plog.infrastructure.ai.StubLlmResponses;
import org.junit.jupiter.api.Test;

class ReportLlmResponseParserTest {

    private final ReportLlmResponseParser parser = new ReportLlmResponseParser(new ObjectMapper());

    @Test
    void parsesTheStubResponseIntoBothMemberAndTeamText() {
        String raw = StubLlmResponses.reportJson();

        MemberReportText member = parser.parseMember(raw);
        TeamReportText team = parser.parseTeam(raw);

        assertThat(member.headline()).contains("리더십");
        assertThat(member.teamMemberHeadline()).isNotBlank().isNotEqualTo(member.headline());
        assertThat(member.strengths()).hasSize(3);
        assertThat(member.strengths().getFirst().title()).isEqualTo("주도성");
        assertThat(member.weakness().suggestions()).hasSize(3);
        assertThat(member.growth().nextAction()).isNotBlank();
        assertThat(member.writing().portfolio()).isNotBlank();
        assertThat(team.strength()).isNotBlank();
        assertThat(team.suggestion()).startsWith("앞으로는");
        assertThat(team.isEmpty()).isFalse();
    }

    // 구조화 출력을 못 쓰는 상황(프로바이더 교체 등)에서 모델이 흔히 붙이는 형태들.
    @Test
    void stripsMarkdownCodeFence() {
        String raw = "```json\n{\"headline\": \"한 줄 평가입니다\"}\n```";

        assertThat(parser.parseMember(raw).headline()).isEqualTo("한 줄 평가입니다");
    }

    @Test
    void stripsFenceWithoutLanguageTag() {
        String raw = "```\n{\"headline\": \"한 줄 평가입니다\"}\n```";

        assertThat(parser.parseMember(raw).headline()).isEqualTo("한 줄 평가입니다");
    }

    @Test
    void ignoresProseAroundTheJsonObject() {
        String raw = "요청하신 분석 결과입니다.\n{\"headline\": \"한 줄 평가입니다\"}\n도움이 되었길 바랍니다.";

        assertThat(parser.parseMember(raw).headline()).isEqualTo("한 줄 평가입니다");
    }

    // 문자열 안의 중괄호에 속아 본문을 일찍 자르면 안 된다.
    @Test
    void doesNotStopAtBracesInsideStringLiterals() {
        String raw = "{\"headline\": \"중괄호 } 와 이스케이프 \\\" 가 들어간 문장\", "
                + "\"growth\": {\"growthPoint\": \"a\", \"keepStrength\": \"b\", \"nextAction\": \"c\"}}";

        MemberReportText parsed = parser.parseMember(raw);

        assertThat(parsed.headline()).isEqualTo("중괄호 } 와 이스케이프 \" 가 들어간 문장");
        assertThat(parsed.growth().nextAction()).isEqualTo("c");
    }

    @Test
    void ignoresUnknownFieldsSoSchemaChangesDoNotBreakParsing() {
        String raw = "{\"headline\": \"한 줄\", \"unexpectedField\": {\"nested\": 1}}";

        assertThat(parser.parseMember(raw).headline()).isEqualTo("한 줄");
    }

    @Test
    void defaultsMissingCollectionsToEmptyInsteadOfNull() {
        MemberReportText parsed = parser.parseMember("{\"headline\": \"한 줄\"}");

        assertThat(parsed.strengths()).isEmpty();
        assertThat(parsed.weakness()).isNull();
    }

    // 토큰 상한에 걸려 잘린 응답. 조용히 통과시키면 반쪽짜리 리포트가 발행된다.
    @Test
    void rejectsTruncatedJson() {
        String raw = "{\"headline\": \"한 줄\", \"strengths\": [{\"title\": \"주도성\"";

        assertThatThrownBy(() -> parser.parseMember(raw))
                .isInstanceOf(LlmGenerationException.class)
                .hasMessageContaining("끊겼");
    }

    @Test
    void rejectsResponseWithoutHeadline() {
        assertThatThrownBy(() -> parser.parseMember("{\"strengths\": []}"))
                .isInstanceOf(LlmGenerationException.class)
                .hasMessageContaining("headline");
    }

    @Test
    void rejectsBlankAndNonJsonResponses() {
        assertThatThrownBy(() -> parser.parseMember("   "))
                .isInstanceOf(LlmGenerationException.class);
        assertThatThrownBy(() -> parser.parseMember("죄송합니다. 답변할 수 없습니다."))
                .isInstanceOf(LlmGenerationException.class);
    }

    @Test
    void rejectsEmptyTeamInsight() {
        assertThatThrownBy(() -> parser.parseTeam("{\"strength\": \"\", \"suggestion\": null}"))
                .isInstanceOf(LlmGenerationException.class)
                .hasMessageContaining("비어");
    }
}
