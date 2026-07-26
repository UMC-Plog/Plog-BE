package com.plog.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailVerificationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 25, 12, 0);

    @Test
    @DisplayName("발급 시 purpose가 고정된다")
    void issueKeepsPurpose() {
        EmailVerification verification = EmailVerification.issue(
                "a@plog.test", EmailVerificationPurpose.PASSWORD_RESET,
                "hash", NOW.plusMinutes(5), NOW);

        assertThat(verification.getPurpose()).isEqualTo(EmailVerificationPurpose.PASSWORD_RESET);
    }

    @Test
    @DisplayName("재발급은 코드와 상태만 갱신하고 purpose는 유지한다")
    void reissueKeepsPurpose() {
        EmailVerification verification = EmailVerification.issue(
                "a@plog.test", EmailVerificationPurpose.SIGNUP,
                "old", NOW.plusMinutes(5), NOW);
        verification.markVerified();

        verification.reissue("new", NOW.plusMinutes(10), NOW.plusMinutes(1));

        assertThat(verification.getPurpose()).isEqualTo(EmailVerificationPurpose.SIGNUP);
        assertThat(verification.isVerified()).isFalse();
        assertThat(verification.getAttemptCount()).isZero();
        assertThat(verification.matches("new")).isTrue();
    }
}
