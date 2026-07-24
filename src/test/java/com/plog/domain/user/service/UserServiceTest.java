package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.dto.request.SignupRequest;
import com.plog.domain.user.entity.AgreementType;
import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.EmailVerificationRepository;
import com.plog.domain.user.repository.UserAgreementRepository;
import com.plog.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserAgreementRepository userAgreementRepository;
    @Mock
    private EmailVerificationRepository emailVerificationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Captor
    private ArgumentCaptor<User> userCaptor;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, userAgreementRepository, emailVerificationRepository, passwordEncoder);
    }

    @Test
    void signupStoresTheChosenProfilePresetOnTheUser() {
        String email = "user@plog.com";
        SignupRequest request = new SignupRequest(
                "홍길동", email, "plog1234", "gildong", ProfilePreset.OTTER,
                List.of(
                        new SignupRequest.AgreementItem(AgreementType.SERVICE_TERMS, true),
                        new SignupRequest.AgreementItem(AgreementType.PRIVACY, true),
                        new SignupRequest.AgreementItem(AgreementType.EXTERNAL_DATA, true)
                ));
        given(passwordEncoder.encode("plog1234")).willReturn("encoded");
        given(emailVerificationRepository.findByEmail(email)).willReturn(Optional.of(verifiedFor(email)));
        given(userRepository.findByEmail(email)).willReturn(Optional.empty());
        given(userRepository.existsByNickname("gildong")).willReturn(false);

        userService.signup(request);

        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getProfilePreset()).isEqualTo(ProfilePreset.OTTER);
    }

    private EmailVerification verifiedFor(String email) {
        EmailVerification verification = EmailVerification.issue(
                email, "hash", LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        verification.markVerified();
        return verification;
    }
}
