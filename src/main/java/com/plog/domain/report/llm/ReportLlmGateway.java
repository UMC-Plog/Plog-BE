package com.plog.domain.report.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.infrastructure.ai.LlmClient;
import com.plog.infrastructure.ai.LlmGenerationException;
import com.plog.infrastructure.ai.LlmProperties;
import com.plog.infrastructure.ai.LlmRequest;
import com.plog.infrastructure.ai.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 리포트 텍스트 생성 한 단위. 프롬프트 조립 → LLM 호출 → 파싱을 묶는다.
 * <p>
 * 오케스트레이션은 이 클래스만 부르면 되고, 프롬프트 파일·스키마·재시도는 여기서 감춘다.
 * <b>트랜잭션 안에서 호출하면 안 된다</b> — 한 번에 수 초~수십 초가 걸린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportLlmGateway {

    private final LlmClient llmClient;
    private final ReportPromptLoader promptLoader;
    private final ReportLlmResponseParser parser;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 멤버 1명분 텍스트 생성.
     * <p>
     * 파싱 실패 시 1회만 다시 시도한다. 같은 프롬프트라도 temperature 가 0 이 아니라 결과가
     * 달라지므로 재시도에 의미가 있다. 전송 실패(5xx·타임아웃)는 클라이언트가 이미 한 번 더 시도한다.
     *
     * @return 생성된 텍스트와 응답 원문
     */
    public GeneratedMemberText generateMemberText(MemberLlmInput input) {
        return generate(
                promptLoader.memberSystemPrompt(),
                input,
                ReportLlmSchemas.memberSchema(),
                (raw, response) -> new GeneratedMemberText(
                        parser.parseMember(raw), raw, response.model())
        );
    }

    /** 팀 인사이트 생성. 프로젝트 1건당 1회. */
    public TeamReportText generateTeamText(TeamLlmInput input) {
        return generate(
                promptLoader.teamSystemPrompt(),
                input,
                ReportLlmSchemas.teamSchema(),
                (raw, response) -> parser.parseTeam(raw)
        );
    }

    private <T> T generate(
            String systemPrompt,
            Object input,
            String schema,
            ResultMapper<T> mapper
    ) {
        String userPrompt = toUserPrompt(input);
        LlmRequest request = new LlmRequest(
                systemPrompt,
                userPrompt,
                schema,
                properties.maxOutputTokens(),
                properties.temperature()
        );

        LlmGenerationException lastFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            LlmResponse response = llmClient.generate(request);
            try {
                return mapper.map(response.text(), response);
            } catch (LlmGenerationException e) {
                // 응답은 왔는데 형식이 어긋난 경우. 한 번 더 뽑아본다.
                lastFailure = e;
                if (attempt < 2) {
                    log.warn("LLM 응답 파싱 실패, 재생성합니다({}/2)", attempt, e);
                }
            }
        }
        throw lastFailure;
    }

    private String toUserPrompt(Object input) {
        try {
            // 입력을 JSON 으로 그대로 넘긴다. 키 이름이 곧 설명이라 별도 템플릿이 필요 없고,
            // 필드가 늘어나도 프롬프트 문자열을 손댈 필요가 없다.
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(input);
        } catch (Exception e) {
            throw new LlmGenerationException("LLM 입력 직렬화에 실패했습니다.", e);
        }
    }

    /** 멤버 텍스트 + 감사용 원문. */
    public record GeneratedMemberText(MemberReportText text, String rawResponse, String model) {
    }

    @FunctionalInterface
    private interface ResultMapper<T> {
        T map(String raw, LlmResponse response);
    }
}
