package com.plog.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이 API 의 공개 오리진. 응답에 절대 URL 을 담을 때 쓴다.
 * <p>
 * 상대 경로로 내보내면 로컬 프론트가 배포 API 를 보는 구성(application-prod.yaml 의
 * localhost:5173 CORS 항목)에서 오리진이 갈려 틀린다.
 * <p>
 * 채팅 첨부 프록시와 게시글·업무카드 첨부 다운로드가 함께 쓰므로 media 아래가 아니라
 * app 바로 아래에 둔다.
 */
@ConfigurationProperties(prefix = "app")
public record ApiProperties(
        String baseUrl
) {
    public ApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.base-url 이 필요합니다.");
        }
    }
}
