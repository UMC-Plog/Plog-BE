package com.plog.domain.report.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.report.service.ReportPdfRecoveryService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportPdfRecoverySchedulerTest {

    @Mock
    private ReportPdfRecoveryService recoveryService;

    @InjectMocks
    private ReportPdfRecoveryScheduler scheduler;

    @Test
    void delegatesStartupRecoveryToTheSameSafeBatch() {
        when(recoveryService.recoverMissingArchives()).thenReturn(1);

        scheduler.recoverOnStartup();

        verify(recoveryService).recoverMissingArchives();
    }

    @Test
    void keepsTheScheduleAliveWhenTheBatchFails() {
        when(recoveryService.recoverMissingArchives())
                .thenThrow(new IllegalStateException("database unavailable"));

        scheduler.recover();

        verify(recoveryService).recoverMissingArchives();
    }

    @Test
    void skipsASecondTickWhileRecoveryIsAlreadyRunning() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(recoveryService.recoverMissingArchives()).thenAnswer(invocation -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return 0;
        });

        Thread first = new Thread(scheduler::recover);
        first.start();
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        scheduler.recover();
        release.countDown();
        first.join(2_000);
        assertThat(first.isAlive()).isFalse();

        verify(recoveryService).recoverMissingArchives();
    }
}
