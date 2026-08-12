package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.entity.RefreshToken;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.RefreshTokenRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProperties;
import com.plog.global.util.HashUtil;
import com.plog.global.util.TimeUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final Duration GRACE = Duration.ofSeconds(60);
    private static final String RAW_TOKEN = "raw-refresh-token";
    private static final Long USER_ID = 7L;

    private final JwtProperties jwtProperties = new JwtProperties(
            "test-secret-key-that-is-long-enough-for-hs256", Duration.ofMinutes(30), REFRESH_TTL,
            Duration.ofDays(14), GRACE);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenRevoker refreshTokenRevoker;

    @Captor
    private ArgumentCaptor<RefreshToken> tokenCaptor;

    private RefreshTokenService service() {
        return new RefreshTokenService(refreshTokenRepository, jwtProperties, refreshTokenRevoker);
    }

    private User user() {
        User user = User.createLocal("user@plog.com", "encoded", "plog", "ploggy");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private RefreshToken livingToken(User user) {
        return RefreshToken.issue(user, HashUtil.sha256Hex(RAW_TOKEN),
                TimeUtil.now().plus(REFRESH_TTL));
    }

    @Test
    void issuesTokensThatExpireRelativeToUtcNow() {
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(call -> call.getArgument(0));

        service().issue(user());

        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getExpiresAt())
                .isCloseTo(TimeUtil.now().plus(REFRESH_TTL), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("첫 사용이면 소모 표시하고 소유자를 돌려준다")
    void firstUseMarksTokenConsumedAndReturnsOwner() {
        User user = user();
        given(refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(RAW_TOKEN)))
                .willReturn(Optional.of(livingToken(user)));
        given(refreshTokenRepository.markUsed(eq(HashUtil.sha256Hex(RAW_TOKEN)), any()))
                .willReturn(1);

        assertThat(service().rotateOrThrow(RAW_TOKEN)).isSameAs(user);

        verify(refreshTokenRevoker, never()).revokeAllSessions(any());
    }

    /**
     * 이 프로젝트에서 실제로 로그아웃을 일으킨 시나리오.
     * PWA를 스와이프로 종료하면 매번 콜드 스타트라 부팅 재발급이 돌고, 그 요청이 겹치거나
     * 모바일 네트워크에서 재시도되면 같은 원문이 두 번 들어온다. 유예 안이면 통과시켜야 한다.
     */
    @Test
    @DisplayName("유예 시간 안의 재사용은 재시도로 보고 허용한다")
    void retryWithinGraceWindowIsAllowed() {
        User user = user();
        given(refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(RAW_TOKEN)))
                .willReturn(Optional.of(livingToken(user)));
        given(refreshTokenRepository.markUsed(eq(HashUtil.sha256Hex(RAW_TOKEN)), any()))
                .willReturn(0);
        given(refreshTokenRepository.findUsedAtByTokenHash(HashUtil.sha256Hex(RAW_TOKEN)))
                .willReturn(Optional.of(TimeUtil.now().minusSeconds(10)));

        assertThat(service().rotateOrThrow(RAW_TOKEN)).isSameAs(user);

        verify(refreshTokenRevoker, never()).revokeAllSessions(any());
    }

    @Test
    @DisplayName("유예를 넘긴 재사용은 탈취로 보고 해당 유저의 모든 세션을 폐기한다")
    void reuseAfterGraceWindowRevokesEverySession() {
        User user = user();
        given(refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(RAW_TOKEN)))
                .willReturn(Optional.of(livingToken(user)));
        given(refreshTokenRepository.markUsed(eq(HashUtil.sha256Hex(RAW_TOKEN)), any()))
                .willReturn(0);
        given(refreshTokenRepository.findUsedAtByTokenHash(HashUtil.sha256Hex(RAW_TOKEN)))
                .willReturn(Optional.of(TimeUtil.now().minusMinutes(5)));

        assertThatThrownBy(() -> service().rotateOrThrow(RAW_TOKEN))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);

        // 같은 트랜잭션에서 지우면 뒤따르는 예외가 폐기까지 롤백시켜 탈취자가 회전을 이어간다.
        // 반드시 별도 트랜잭션(REQUIRES_NEW)으로 나가야 한다.
        verify(refreshTokenRevoker).revokeAllSessions(USER_ID);
        verify(refreshTokenRepository, never()).deleteAllByUserId(any());
    }

    /** 회전이 지우지 않고 표시만 하므로, 이 배치가 없으면 refresh_token 이 무한히 커진다. */
    @Test
    @DisplayName("만료됐거나 유예가 끝난 토큰을 정리한다")
    void purgesExpiredAndGraceEndedTokens() {
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> usedBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        given(refreshTokenRepository.deleteExhausted(any(), any())).willReturn(3);

        assertThat(service().purgeExhausted()).isEqualTo(3);

        verify(refreshTokenRepository).deleteExhausted(now.capture(), usedBefore.capture());
        // 유예가 끝난 것만 지운다 — 유예 안의 토큰을 지우면 재시도가 다시 로그아웃된다.
        assertThat(usedBefore.getValue())
                .isCloseTo(now.getValue().minus(GRACE), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("존재하지 않는 토큰은 거부한다")
    void unknownTokenIsRejected() {
        given(refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(RAW_TOKEN)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service().rotateOrThrow(RAW_TOKEN))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("만료된 토큰은 유예와 무관하게 거부한다")
    void expiredTokenIsRejected() {
        User user = user();
        RefreshToken expired = RefreshToken.issue(user, HashUtil.sha256Hex(RAW_TOKEN),
                TimeUtil.now().minusSeconds(1));
        given(refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(RAW_TOKEN)))
                .willReturn(Optional.of(expired));

        assertThatThrownBy(() -> service().rotateOrThrow(RAW_TOKEN))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);

        verify(refreshTokenRepository, never()).markUsed(any(), any());
    }
}
