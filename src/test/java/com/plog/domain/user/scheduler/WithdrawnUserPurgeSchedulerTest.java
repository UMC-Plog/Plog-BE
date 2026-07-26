package com.plog.domain.user.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.service.UserWithdrawalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WithdrawnUserPurgeSchedulerTest {

    @Test
    @DisplayName("스케줄러는 UserWithdrawalService.purgeExpired()에 위임한다")
    void delegatesToPurgeExpired() {
        UserWithdrawalService userWithdrawalService = mock(UserWithdrawalService.class);
        given(userWithdrawalService.purgeExpired()).willReturn(3);
        WithdrawnUserPurgeScheduler scheduler = new WithdrawnUserPurgeScheduler(userWithdrawalService);

        scheduler.purge();

        verify(userWithdrawalService, times(1)).purgeExpired();
    }

    @Test
    @DisplayName("배치 자체가 실패해도 예외를 삼켜 다음 스케줄을 살린다")
    void swallowsBatchFailure() {
        // 행 단위 실패는 purgeExpired가 자체 처리한다(ERROR 로깅 후 계속). 여기로 올라오는 건
        // 대상 조회 실패처럼 배치 전체가 못 도는 경우다 — 스케줄러가 터지면 로그도 우리 메시지가 아니게 된다.
        UserWithdrawalService userWithdrawalService = mock(UserWithdrawalService.class);
        given(userWithdrawalService.purgeExpired()).willThrow(new IllegalStateException("DB down"));
        WithdrawnUserPurgeScheduler scheduler = new WithdrawnUserPurgeScheduler(userWithdrawalService);

        assertThatCode(scheduler::purge).doesNotThrowAnyException();

        verify(userWithdrawalService, times(1)).purgeExpired();
    }

    @Test
    @DisplayName("파기 대상이 0건이어도 예외 없이 끝난다")
    void doesNotFailWhenNothingToPurge() {
        UserWithdrawalService userWithdrawalService = mock(UserWithdrawalService.class);
        given(userWithdrawalService.purgeExpired()).willReturn(0);
        WithdrawnUserPurgeScheduler scheduler = new WithdrawnUserPurgeScheduler(userWithdrawalService);

        assertThatCode(scheduler::purge).doesNotThrowAnyException();

        verify(userWithdrawalService, times(1)).purgeExpired();
    }
}
