package com.plog.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialSignupPropertiesTest {

    @Test
    @DisplayName("티켓 TTL이 없으면 기동을 중단한다")
    void rejectsNullTtl() {
        assertThatThrownBy(() -> new SocialSignupProperties(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("티켓 TTL이 0 이하이면 기동을 중단한다")
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new SocialSignupProperties(Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("양수 TTL은 그대로 바인딩된다")
    void acceptsPositiveTtl() {
        assertThat(new SocialSignupProperties(Duration.ofMinutes(30)).ticketTtl())
                .isEqualTo(Duration.ofMinutes(30));
    }
}
