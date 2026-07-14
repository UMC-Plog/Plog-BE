package com.plog.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.email-verification.* 바인딩. 인증 코드 정책값을 하드코딩하지 않고 yml에서 주입받는다.
 */
@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(
        int codeLength,
        Duration ttl,
        int maxAttempts,
        Duration resendCooldown
) {
    public EmailVerificationProperties {
        if (codeLength < 4) {
            throw new IllegalStateException("app.email-verification.code-length 는 4 이상이어야 합니다.");
        }
        if (ttl == null || resendCooldown == null) {
            throw new IllegalStateException("app.email-verification.ttl / resend-cooldown 이 필요합니다.");
        }
        if (maxAttempts < 1) {
            throw new IllegalStateException("app.email-verification.max-attempts 는 1 이상이어야 합니다.");
        }
    }
}
