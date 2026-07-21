package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.repository.EmailVerificationRepository;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.config.EmailVerificationProperties;
import com.plog.infrastructure.mail.MailSender;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final EmailVerificationProperties properties =
            new EmailVerificationProperties(6, TTL, 5, Duration.ofSeconds(60));

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailSender mailSender;

    @Captor
    private ArgumentCaptor<EmailVerification> verificationCaptor;

    @Test
    void issuesCodesThatExpireRelativeToUtcNow() {
        EmailVerificationService service = new EmailVerificationService(
                emailVerificationRepository, userRepository, mailSender, properties);
        given(userRepository.findByEmail("user@plog.com")).willReturn(Optional.empty());
        given(emailVerificationRepository.findByEmail("user@plog.com")).willReturn(Optional.empty());
        given(emailVerificationRepository.save(any(EmailVerification.class)))
                .willAnswer(call -> call.getArgument(0));

        service.sendCode("user@plog.com");

        org.mockito.Mockito.verify(emailVerificationRepository).save(verificationCaptor.capture());
        assertThat(verificationCaptor.getValue().getExpiresAt())
                .isCloseTo(LocalDateTime.now(ZoneOffset.UTC).plus(TTL), within(5, ChronoUnit.SECONDS));
    }
}
