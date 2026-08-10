package com.plog.domain.report.llm;

/**
 * 구조화 출력용 JSON 스키마(OpenAPI subset). Gemini 의 {@code responseSchema} 로 넘어가
 * 응답 형태를 강제한다.
 * <p>
 * 스키마를 넘기면 코드펜스나 설명문이 섞이지 않으므로 파싱 실패가 거의 사라진다.
 * 그래도 {@code ReportLlmResponseParser} 는 방어적으로 짠다 — Stub 이나 스키마 미지원
 * 프로바이더로 갈아끼울 수 있어야 하기 때문이다.
 * <p>
 * {@code required} 를 최소로 둔 건 의도다. 근거가 부족하면 비우라고 지시해 놓고 스키마로
 * 필수화하면, 모델이 빈칸을 채우려고 없는 사실을 지어낸다.
 */
public final class ReportLlmSchemas {

    private ReportLlmSchemas() {
    }

    public static String memberSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "headline": { "type": "string" },
                    "teamMemberHeadline": { "type": "string" },
                    "strengths": {
                      "type": "array",
                      "minItems": 3,
                      "maxItems": 3,
                      "items": {
                        "type": "object",
                        "properties": {
                          "title": { "type": "string" },
                          "description": { "type": "string" }
                        },
                        "required": ["title", "description"]
                      }
                    },
                    "weakness": {
                      "type": "object",
                      "properties": {
                        "title": { "type": "string" },
                        "suggestions": {
                          "type": "array",
                          "minItems": 3,
                          "maxItems": 3,
                          "items": { "type": "string" }
                        }
                      },
                      "required": ["title", "suggestions"]
                    },
                    "growth": {
                      "type": "object",
                      "properties": {
                        "growthPoint": { "type": "string" },
                        "keepStrength": { "type": "string" },
                        "nextAction": { "type": "string" }
                      },
                      "required": ["growthPoint", "keepStrength", "nextAction"]
                    },
                    "writing": {
                      "type": "object",
                      "properties": {
                        "coverLetter": { "type": "string" },
                        "portfolio": { "type": "string" }
                      },
                      "required": ["coverLetter", "portfolio"]
                    }
                  },
                  "required": ["headline", "teamMemberHeadline"]
                }
                """;
    }

    public static String teamSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "strength": { "type": "string" },
                    "suggestion": { "type": "string" }
                  },
                  "required": ["strength", "suggestion"]
                }
                """;
    }
}
