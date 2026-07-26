package com.plog.domain.user.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.service.SocialSignupTicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialSignupTicketPurgeSchedulerTest {

    @Test
    @DisplayName("만료 티켓 정리를 위임한다")
    void delegatesPurge() {
        SocialSignupTicketService service = mock(SocialSignupTicketService.class);
        given(service.purgeFinished()).willReturn(3);

        new SocialSignupTicketPurgeScheduler(service).purge();

        verify(service).purgeFinished();
    }

    @Test
    @DisplayName("정리 실패가 스케줄러 밖으로 전파되지 않는다")
    void swallowsFailure() {
        SocialSignupTicketService service = mock(SocialSignupTicketService.class);
        given(service.purgeFinished()).willThrow(new IllegalStateException("db down"));

        assertThatCode(() -> new SocialSignupTicketPurgeScheduler(service).purge())
                .doesNotThrowAnyException();
    }
}
