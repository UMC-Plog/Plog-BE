package com.plog.infrastructure.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 썸네일 파이프라인 설정.
 * <p>
 * enabled 를 첨부 확정 시점에도 확인한다. 꺼진 환경에서 PENDING 이 쌓이면 나중에
 * 켤 때 밀린 작업이 한꺼번에 Lambda 로 터진다.
 */
@ConfigurationProperties(prefix = "plog.thumbnail")
public record ThumbnailProperties(
        boolean enabled,
        String functionName,
        int maxEdge
) {
    public ThumbnailProperties {
        if (enabled && (functionName == null || functionName.isBlank())) {
            throw new IllegalStateException("plog.thumbnail.function-name 이 필요합니다.");
        }
        if (maxEdge <= 0) {
            maxEdge = 640;
        }
    }
}
