package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.plog.domain.user.entity.RefreshToken;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.RefreshTokenRepository;
import com.plog.global.security.jwt.JwtProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private final JwtProperties jwtProperties = new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hs256", Duration.ofMinutes(30), REFRESH_TTL);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Captor
    private ArgumentCaptor<RefreshToken> tokenCaptor;

    @Test
    void issuesTokensThatExpireRelativeToUtcNow() {
        RefreshTokenService service = new RefreshTokenService(refreshTokenRepository, jwtProperties);
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(call -> call.getArgument(0));

        User user = User.createLocal("user@plog.com", "encoded", "plog", "ploggy");

        service.issue(user);

        org.mockito.Mockito.verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getExpiresAt())
                .isCloseTo(LocalDateTime.now(ZoneOffset.UTC).plus(REFRESH_TTL),
                        within(5, ChronoUnit.SECONDS));
    }
}
