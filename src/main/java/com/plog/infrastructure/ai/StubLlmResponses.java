package com.plog.infrastructure.ai;

/**
 * Stub 클라이언트가 돌려줄 고정 응답.
 * <p>
 * 값은 리포트 시안의 실제 문구를 그대로 가져왔다. 길이·문체·필드 구성이 화면과 같아야
 * 키 없이 돌려볼 때도 "화면에 실제로 들어갈 만한 결과인지"가 드러난다.
 * <p>
 * 멤버 스키마와 팀 스키마의 필드를 한 오브젝트에 모두 담는다 — 파서가
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} 라 각자 필요한 것만 읽어 간다.
 * 덕분에 Stub 이 요청 종류를 구분하지 않아도 된다.
 */
public final class StubLlmResponses {

    private StubLlmResponses() {
    }

    public static String reportJson() {
        return """
                {
                  "headline": "적극적인 리더십으로 팀의 방향을 잡고, 구성원들이 원활하게 협업할 수 있도록 분위기를 주도했어요",
                  "strengths": [
                    { "title": "주도성", "description": "일정을 주도적으로 관리하고 실행해요" },
                    { "title": "전문성", "description": "기술 역량으로 팀의 완성도를 높여요" },
                    { "title": "소통 능력", "description": "명확한 소통으로 협업을 원활히 해요" }
                  ],
                  "weakness": {
                    "title": "의견 제시 빈도가 상대적으로 낮음",
                    "suggestions": [
                      "이슈에 대한 의견 제시를 늘려보세요",
                      "회의에 더 적극적으로 참여해 보세요",
                      "아이디어를 공유하면 성과가 높아져요"
                    ]
                  },
                  "growth": {
                    "growthPoint": "초기 논의 단계에서 의견을 제시해 팀 방향 설정에 기여해요",
                    "keepStrength": "책임감과 문제 해결력을 계속 발휘해서 팀을 이끌어 가세요",
                    "nextAction": "앞으로 2주 동안 최소 1개의 아이디어를 제안하세요"
                  },
                  "writing": {
                    "coverLetter": "프로젝트의 초기 기획부터 요구사항 정의, 설계, 개발, 배포까지 전 과정을 주도하며 일정 내 고품질 결과물을 안정적으로 제공하고, 팀과의 긴밀한 협업을 통해 프로젝트 목표 달성에 크게 기여했습니다.",
                    "portfolio": "웹 개발 프로젝트에서 프론트엔드 개발을 주도하며, 주요 페이지를 단독 개발했습니다. 사용자 경험을 고려한 UI/UX 설계와 성능 최적화를 통해 완성도에 기여했습니다."
                  },
                  "strength": "팀원들은 각자의 역할을 안정적으로 수행하며, 진행 상황과 의견을 꾸준히 공유해 원활한 협업 흐름을 만들었습니다",
                  "suggestion": "앞으로는 초기 단계에서 우선순위와 의사결정 기준을 더욱 명확히 정한다면 협업 효율을 높일 수 있습니다"
                }
                """;
    }
}
