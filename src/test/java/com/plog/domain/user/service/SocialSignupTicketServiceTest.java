package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.SocialSignupTicket;
import com.plog.domain.user.repository.SocialSignupTicketRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.SocialSignupProperties;
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

class SocialSignupTicketServiceTest {

    private static final SocialIdentity IDENTITY =
            new SocialIdentity(ProviderType.KAKAO, "kakao-1", "a@plog.test");

    private SocialSignupTicketRepository ticketRepository;
    private SocialSignupTicketService service;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(SocialSignupTicketRepository.class);
        service = new SocialSignupTicketService(
                ticketRepository, new SocialSignupProperties(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("발급 시 원문을 반환하고 저장 행에는 해시만 남긴다")
    void issueStoresHashOnly() {
        String raw = service.issue(IDENTITY);

        ArgumentCaptor<SocialSignupTicket> captor = ArgumentCaptor.forClass(SocialSignupTicket.class);
        verify(ticketRepository).save(captor.capture());
        SocialSignupTicket saved = captor.getValue();

        assertThat(saved.getTicketHash()).isEqualTo(HashUtil.sha256Hex(raw));
        assertThat(saved.getTicketHash()).isNotEqualTo(raw);
        assertThat(saved.getEmail()).isEqualTo("a@plog.test");
    }

    @Test
    @DisplayName("발급 전에 같은 소셜 계정의 이전 티켓을 지운다")
    void issueRemovesPreviousTicket() {
        service.issue(IDENTITY);

        verify(ticketRepository).deleteByProviderTypeAndProviderId(ProviderType.KAKAO, "kakao-1");
    }

    @Test
    @DisplayName("존재하지 않는 티켓은 SOCIAL_TICKET_INVALID")
    void consumeRejectsUnknownTicket() {
        given(ticketRepository.findByTicketHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeOrThrow("raw"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_TICKET_INVALID);
    }

    @Test
    @DisplayName("빈 티켓 문자열은 조회 없이 SOCIAL_TICKET_INVALID")
    void consumeRejectsBlankTicket() {
        assertThatThrownBy(() -> service.consumeOrThrow("  "))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_TICKET_INVALID);
    }

    @Test
    @DisplayName("만료된 티켓은 SOCIAL_TICKET_EXPIRED")
    void consumeRejectsExpiredTicket() {
        LocalDateTime past = TimeUtil.nowUtc().minusMinutes(1);
        given(ticketRepository.findByTicketHash(any())).willReturn(Optional.of(ticket(past)));

        assertThatThrownBy(() -> service.consumeOrThrow("raw"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_TICKET_EXPIRED);
    }

    @Test
    @DisplayName("조건부 소비가 0행이면(이미 사용된 티켓) SOCIAL_TICKET_INVALID")
    void consumeRejectsAlreadyConsumedTicket() {
        given(ticketRepository.findByTicketHash(any()))
                .willReturn(Optional.of(ticket(TimeUtil.nowUtc().plusMinutes(10))));
        given(ticketRepository.consume(anyLong(), any())).willReturn(0);

        assertThatThrownBy(() -> service.consumeOrThrow("raw"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_TICKET_INVALID);
    }

    @Test
    @DisplayName("정상 티켓은 소비 후 반환한다")
    void consumeReturnsTicket() {
        SocialSignupTicket valid = ticket(TimeUtil.nowUtc().plusMinutes(10));
        given(ticketRepository.findByTicketHash(HashUtil.sha256Hex("raw")))
                .willReturn(Optional.of(valid));
        given(ticketRepository.consume(eq(7L), any())).willReturn(1);

        assertThat(service.consumeOrThrow("raw")).isSameAs(valid);
    }

    @Test
    @DisplayName("정리는 만료 행과 이미 소비된 행을 함께 지운다")
    void purgeFinishedDelegates() {
        given(ticketRepository.deleteExpiredOrConsumed(any())).willReturn(4);

        assertThat(service.purgeFinished()).isEqualTo(4);
    }

    private SocialSignupTicket ticket(LocalDateTime expiresAt) {
        SocialSignupTicket ticket = SocialSignupTicket.issue(
                ProviderType.KAKAO, "kakao-1", "a@plog.test", HashUtil.sha256Hex("raw"), expiresAt);
        ReflectionTestUtils.setField(ticket, "id", 7L);
        return ticket;
    }
}
