package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailAvailabilityServiceTest {

    private static final String EMAIL = "a@plog.test";

    private UserRepository userRepository;
    private EmailAvailabilityService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new EmailAvailabilityService(userRepository);
    }

    @Test
    @DisplayName("가입 이력이 없으면 통과한다")
    void allowsUnusedEmail() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatCode(() -> service.assertAvailableForSignup(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("로컬 계정이 있으면 EMAIL_DUPLICATED_LOCAL")
    void rejectsLocalAccount() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(
                User.createLocal(EMAIL, "encoded", "홍길동", "바나나", ProfilePreset.OTTER)));

        assertThatThrownBy(() -> service.assertAvailableForSignup(EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_DUPLICATED_LOCAL);
    }

    @Test
    @DisplayName("소셜 계정이 있으면 EMAIL_DUPLICATED_SOCIAL")
    void rejectsSocialAccount() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(
                User.createSocial(EMAIL, "홍길동", "바나나", ProviderType.KAKAO, "kakao-1")));

        assertThatThrownBy(() -> service.assertAvailableForSignup(EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_DUPLICATED_SOCIAL);
    }

    @Test
    @DisplayName("탈퇴 유예 중인 계정이면 EMAIL_WITHDRAWAL_PENDING이 우선한다")
    void rejectsWithdrawnAccountFirst() {
        User withdrawn = User.createLocal(EMAIL, "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
        withdrawn.withdraw(LocalDateTime.of(2026, 7, 25, 12, 0));
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.assertAvailableForSignup(EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_WITHDRAWAL_PENDING);
    }
}
