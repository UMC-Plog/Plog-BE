package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.entity.EmailVerificationPurpose;
import com.plog.domain.user.repository.EmailVerificationRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.EmailVerificationProperties;
import com.plog.global.util.HashUtil;
import com.plog.global.util.TimeUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class EmailVerificationCodeServiceTest {

    private static final String EMAIL = "a@plog.test";
    private static final EmailVerificationPurpose PURPOSE = EmailVerificationPurpose.SIGNUP;

    private EmailVerificationRepository verificationRepository;
    private EmailVerificationProperties properties;
    private EmailVerificationCodeService service;

    @BeforeEach
    void setUp() {
        verificationRepository = mock(EmailVerificationRepository.class);
        properties = new EmailVerificationProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60));
        service = new EmailVerificationCodeService(verificationRepository, properties);
    }

    @Test
    @DisplayName("발급 시 원문 코드를 반환하고 저장 행에는 SHA-256 해시만 저장한다")
    void issueCodeStoresHashNotRawCode() {
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.empty());

        String rawCode = service.issueCode(EMAIL, PURPOSE);

        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(verificationRepository).save(captor.capture());
        EmailVerification saved = captor.getValue();

        assertThat(saved.getCodeHash()).isEqualTo(HashUtil.sha256Hex(rawCode));
        assertThat(saved.getCodeHash()).isNotEqualTo(rawCode);
    }

    @Test
    @DisplayName("재전송 쿨다운 안에서는 VERIFICATION_RESEND_COOLDOWN 예외를 던지고 저장하지 않는다")
    void issueCodeRejectsWithinCooldown() {
        LocalDateTime now = TimeUtil.nowUtc();
        EmailVerification existing = EmailVerification.issue(
                EMAIL, PURPOSE, HashUtil.sha256Hex("111111"), now.plusMinutes(5), now);
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.issueCode(EMAIL, PURPOSE))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_RESEND_COOLDOWN);

        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("쿨다운이 지난 기존 행은 같은 인스턴스를 재발급하며 시도횟수와 인증상태를 초기화한다")
    void issueCodeReissuesExistingRowPastCooldown() {
        LocalDateTime now = TimeUtil.nowUtc();
        EmailVerification existing = EmailVerification.issue(
                EMAIL, PURPOSE, HashUtil.sha256Hex("111111"), now.minusMinutes(10),
                now.minus(properties.resendCooldown()).minusSeconds(5));
        existing.increaseAttempt();
        existing.increaseAttempt();
        existing.markVerified();
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.of(existing));

        String rawCode = service.issueCode(EMAIL, PURPOSE);

        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(verificationRepository).save(captor.capture());
        EmailVerification saved = captor.getValue();

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getAttemptCount()).isEqualTo(0);
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getCodeHash()).isEqualTo(HashUtil.sha256Hex(rawCode));
    }

    @Test
    @DisplayName("코드 불일치 시 DB에서 원자적으로 시도횟수를 올린 뒤에야 예외를 던진다")
    void verifyCodeIncrementsAttemptAtomicallyBeforeThrowingOnMismatch() {
        LocalDateTime now = TimeUtil.nowUtc();
        EmailVerification existing = EmailVerification.issue(
                EMAIL, PURPOSE, HashUtil.sha256Hex("111111"), now.plusMinutes(5), now);
        ReflectionTestUtils.setField(existing, "id", 7L);
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.verifyCode(EMAIL, PURPOSE, "000000"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_MISMATCH);

        // 엔티티를 읽고-더해-save 하면 동시 오답 요청이 서로의 증가분을 덮어써 최대 시도 제한이 뚫린다.
        // 반드시 DB의 원자적 UPDATE 한 번이어야 하고, 엔티티 경로로는 아무것도 쓰지 않아야 한다.
        verify(verificationRepository).increaseAttemptCount(7L);
        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("시도횟수 초과는 만료보다 우선한다 (둘 다 해당되는 행이면 초과 예외가 발생)")
    void verifyCodeAttemptExceededTakesPrecedenceOverExpired() {
        LocalDateTime now = TimeUtil.nowUtc();
        EmailVerification existing = EmailVerification.issue(
                EMAIL, PURPOSE, HashUtil.sha256Hex("111111"), now.minusMinutes(1), now.minusMinutes(10));
        for (int i = 0; i < properties.maxAttempts(); i++) {
            existing.increaseAttempt();
        }
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.verifyCode(EMAIL, PURPOSE, "111111"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
    }

    @Test
    @DisplayName("시도횟수가 남아있는 만료 행은 VERIFICATION_CODE_EXPIRED 예외를 던진다")
    void verifyCodeExpiredWithAttemptsRemaining() {
        LocalDateTime now = TimeUtil.nowUtc();
        EmailVerification existing = EmailVerification.issue(
                EMAIL, PURPOSE, HashUtil.sha256Hex("111111"), now.minusMinutes(1), now.minusMinutes(10));
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.verifyCode(EMAIL, PURPOSE, "111111"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
    }

    @Test
    @DisplayName("정확한 코드로 검증하면 인증 완료 상태로 저장한다")
    void verifyCodeMarksVerifiedOnMatch() {
        LocalDateTime now = TimeUtil.nowUtc();
        EmailVerification existing = EmailVerification.issue(
                EMAIL, PURPOSE, HashUtil.sha256Hex("111111"), now.plusMinutes(5), now);
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.of(existing));

        service.verifyCode(EMAIL, PURPOSE, "111111");

        assertThat(existing.isVerified()).isTrue();
        verify(verificationRepository).save(existing);
    }

    @Test
    @DisplayName("행이 없으면 EMAIL_NOT_VERIFIED 예외를 던진다")
    void requireVerifiedThrowsWhenRowMissing() {
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireVerified(EMAIL, PURPOSE))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("행은 있지만 인증되지 않았으면 EMAIL_NOT_VERIFIED 예외를 던진다")
    void requireVerifiedThrowsWhenNotVerified() {
        LocalDateTime now = TimeUtil.nowUtc();
        EmailVerification existing = EmailVerification.issue(
                EMAIL, PURPOSE, HashUtil.sha256Hex("111111"), now.plusMinutes(5), now);
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.requireVerified(EMAIL, PURPOSE))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("이메일 기준 전체 삭제는 목적을 가리지 않는다 (탈퇴 시 실제 이메일이 남지 않게)")
    void deleteAllByEmailRemovesEveryPurpose() {
        // consume()은 성공한 흐름 1건만 지운다 → 재설정 코드만 받고 버린 행(=실제 이메일)이 무기한 남는다.
        // 탈퇴 시점에 이 메서드로 목적 무관하게 정리해야 개인정보 파기 약속이 실제로 지켜진다.
        service.deleteAllByEmail(EMAIL);

        verify(verificationRepository).deleteAllByEmail(EMAIL);
    }

    @Test
    @DisplayName("인증 완료된 행은 그대로 반환한다")
    void requireVerifiedReturnsRowWhenVerified() {
        LocalDateTime now = TimeUtil.nowUtc();
        EmailVerification existing = EmailVerification.issue(
                EMAIL, PURPOSE, HashUtil.sha256Hex("111111"), now.plusMinutes(5), now);
        existing.markVerified();
        given(verificationRepository.findByEmailAndPurpose(EMAIL, PURPOSE)).willReturn(Optional.of(existing));

        EmailVerification result = service.requireVerified(EMAIL, PURPOSE);

        assertThat(result).isSameAs(existing);
    }
}
