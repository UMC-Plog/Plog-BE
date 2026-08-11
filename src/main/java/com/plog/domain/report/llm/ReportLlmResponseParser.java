package com.plog.domain.report.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.infrastructure.ai.LlmGenerationException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * LLM 응답 텍스트 → 리포트 텍스트 DTO.
 * <p>
 * 구조화 출력을 쓰면 순수 JSON 이 오지만, 파서는 그걸 믿지 않는다. 프로바이더를 바꾸거나
 * 스키마를 못 쓰는 상황이 오면 모델은 곧잘 ```json 펜스나 앞뒤 설명문을 붙인다 —
 * 그때 리포트 생성이 통째로 실패하지 않도록 방어적으로 벗겨낸다.
 */
@Component
@RequiredArgsConstructor
public class ReportLlmResponseParser {

    private final ObjectMapper objectMapper;

    public MemberReportText parseMember(String raw) {
        MemberReportText parsed = parse(raw, MemberReportText.class);
        // 개인 headline은 최소 산출물이다. teamMemberHeadline은 과거 응답에 한해 headline으로
        // 폴백하고, 최신 구조화 스키마에서는 별도 필드로 강제한다.
        if (parsed.headline() == null || parsed.headline().isBlank()) {
            throw new LlmGenerationException("LLM 응답에 headline 이 없습니다: " + preview(raw));
        }
        return parsed;
    }

    public TeamReportText parseTeam(String raw) {
        TeamReportText parsed = parse(raw, TeamReportText.class);
        if (parsed.isEmpty()) {
            throw new LlmGenerationException("LLM 팀 인사이트 응답이 비어 있습니다: " + preview(raw));
        }
        return parsed;
    }

    private <T> T parse(String raw, Class<T> type) {
        if (raw == null || raw.isBlank()) {
            throw new LlmGenerationException("LLM 응답이 비어 있습니다.");
        }
        String json = stripToJsonObject(raw);
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new LlmGenerationException("LLM 응답 JSON 파싱에 실패했습니다: " + preview(raw), e);
        }
    }

    /**
     * 코드펜스와 앞뒤 잡텍스트를 걷어내고 JSON 오브젝트 본문만 남긴다.
     * 문자열 리터럴 안의 중괄호에 속지 않도록 따옴표·이스케이프 상태를 따라가며 짝을 센다.
     */
    private String stripToJsonObject(String raw) {
        String text = raw.strip();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                // ```json 같은 언어 태그 줄을 통째로 버린다
                text = text.substring(firstLineEnd + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
            text = text.strip();
        }

        int start = text.indexOf('{');
        if (start < 0) {
            throw new LlmGenerationException("LLM 응답에서 JSON 오브젝트를 찾지 못했습니다: " + preview(raw));
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        // 여기 오면 중괄호가 안 닫힌 것 — 토큰 상한에 걸려 잘린 응답이 대표적이다.
        throw new LlmGenerationException("LLM 응답 JSON 이 중간에 끊겼습니다: " + preview(raw));
    }

    private String preview(String raw) {
        if (raw == null) {
            return "null";
        }
        String normalized = raw.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 200
                ? normalized
                : normalized.substring(0, 200).toLowerCase(Locale.ROOT) + "...(생략)";
    }
}
