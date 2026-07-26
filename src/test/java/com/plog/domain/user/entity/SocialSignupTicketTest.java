package com.plog.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialSignupTicketTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);

    private SocialSignupTicket ticket(LocalDateTime expiresAt) {
        return SocialSignupTicket.issue(ProviderType.KAKAO, "kakao-1", "a@plog.test", "hash", expiresAt);
    }

    @Test
    @DisplayName("만료 시각이 지나면 만료로 판정한다")
    void expired() {
        assertThat(ticket(NOW.minusSeconds(1)).isExpired(NOW)).isTrue();
        assertThat(ticket(NOW.plusMinutes(30)).isExpired(NOW)).isFalse();
    }

    @Test
    @DisplayName("발급 직후에는 소비되지 않은 상태다")
    void notConsumedOnIssue() {
        assertThat(ticket(NOW.plusMinutes(30)).getConsumedAt()).isNull();
    }

    @Test
    @DisplayName("발급 시 신원 정보를 그대로 보관한다")
    void keepsIdentity() {
        SocialSignupTicket issued = ticket(NOW.plusMinutes(30));

        assertThat(issued.getProviderType()).isEqualTo(ProviderType.KAKAO);
        assertThat(issued.getProviderId()).isEqualTo("kakao-1");
        assertThat(issued.getEmail()).isEqualTo("a@plog.test");
        assertThat(issued.getTicketHash()).isEqualTo("hash");
    }
}
