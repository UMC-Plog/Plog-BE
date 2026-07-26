package com.plog.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.withdrawal.* 바인딩. 탈퇴 후 개인정보를 파기할 때까지의 유예 기간.
 * 화면에 "계정 데이터는 7일 후 완전히 삭제됩니다"로 노출되는 값이므로 하드코딩하지 않는다.
 */
@ConfigurationProperties(prefix = "app.withdrawal")
public record WithdrawalProperties(
        Duration retention
) {
    public WithdrawalProperties {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalStateException("app.withdrawal.retention 은 양수 기간이어야 합니다.");
        }
    }
}
