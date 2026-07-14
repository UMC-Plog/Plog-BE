package com.plog.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.cors.* 바인딩. 허용 오리진은 환경별로 다르므로 하드코딩 금지 → 프로퍼티로 주입.
 * 최소 하나는 있어야 한다(누락 시 조용히 전부 차단되는 것보다 기동 실패가 낫다).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("app.cors.allowed-origins 이 최소 하나 필요합니다.");
        }
    }
}
