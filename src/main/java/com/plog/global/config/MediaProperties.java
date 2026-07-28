package com.plog.global.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.media.* 바인딩.
 * <p>
 * baseUrl: 채팅 첨부 프록시 URL을 절대 경로로 조립할 때 쓰는 API 오리진. 상대 경로로
 * 내보내면 로컬 프론트가 배포 API를 보는 구성에서 오리진이 갈려 틀린다.
 * <p>
 * cookieSameSite: 프론트가 배포 API에 크로스사이트로 붙어 개발하므로 기본값은 None.
 * 프론트가 백엔드를 로컬로 띄우고 vercel.app alias 접속을 버리면 Lax 로 조일 수 있다.
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        String baseUrl,
        String cookieSameSite
) {
    // 대소문자까지 정확해야 한다. "none" 처럼 적으면 ResponseCookie 는 그대로 내보내고
    // 브라우저는 Lax 로 취급해, 크로스사이트에서 이미지만 조용히 깨진다. 기동 시점에 잡는다.
    private static final Set<String> VALID_SAME_SITE = Set.of("Strict", "Lax", "None");

    public MediaProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.media.base-url 이 필요합니다.");
        }
        if (cookieSameSite == null || !VALID_SAME_SITE.contains(cookieSameSite)) {
            throw new IllegalStateException(
                    "app.media.cookie-same-site 는 " + VALID_SAME_SITE + " 중 하나여야 합니다. (대소문자 구분)");
        }
    }
}
