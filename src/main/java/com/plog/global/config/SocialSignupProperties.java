package com.plog.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.social-signup.* 바인딩. 소셜 가입 티켓의 수명을 yml에서 주입받는다.
 * 약관 2화면 + 프로필 1화면을 채우는 데 걸리는 시간이라 이메일 인증 TTL(5분)보다 길다.
 */
@ConfigurationProperties(prefix = "app.social-signup")
public record SocialSignupProperties(Duration ticketTtl) {
    public SocialSignupProperties {
        if (ticketTtl == null || ticketTtl.isZero() || ticketTtl.isNegative()) {
            throw new IllegalStateException("app.social-signup.ticket-ttl 은 양수여야 합니다.");
        }
    }
}
