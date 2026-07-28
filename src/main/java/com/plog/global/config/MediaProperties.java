package com.plog.global.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.media.* 바인딩. 채팅 미디어 쿠키 전용 설정.
 * <p>
 * cookieSameSite: 프론트가 배포 API에 크로스사이트로 붙어 개발하므로 기본값은 None.
 * 프론트가 백엔드를 로컬로 띄우고 vercel.app alias 접속을 버리면 Lax 로 조일 수 있다.
 * <p>
 * 절대 URL 조립용 오리진은 채팅 전용이 아니게 되어 ApiProperties 로 옮겼다.
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        String cookieSameSite
) {
    // 대소문자까지 정확해야 한다. "none" 처럼 적으면 ResponseCookie 는 그대로 내보내고
    // 브라우저는 Lax 로 취급해, 크로스사이트에서 이미지만 조용히 깨진다. 기동 시점에 잡는다.
    private static final Set<String> VALID_SAME_SITE = Set.of("Strict", "Lax", "None");

    public MediaProperties {
        if (cookieSameSite == null || !VALID_SAME_SITE.contains(cookieSameSite)) {
            throw new IllegalStateException(
                    "app.media.cookie-same-site 는 " + VALID_SAME_SITE + " 중 하나여야 합니다. (대소문자 구분)");
        }
    }
}
