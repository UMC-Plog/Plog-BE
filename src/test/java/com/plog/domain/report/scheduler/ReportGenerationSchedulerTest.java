package com.plog.domain.report.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.report.service.ReportBatchResult;
import com.plog.domain.report.service.ReportBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ReportGenerationSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReportGenerationScheduler.class))
            .withUserConfiguration(BatchServiceConfig.class);

    // 파이프라인 완성 전에 켜지면 GENERATING 리포트만 쌓인다 → 기본값은 반드시 꺼짐이어야 한다.
    @Test
    void isDisabledByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(ReportGenerationScheduler.class));
    }

    @Test
    void isDisabledWhenExplicitlyTurnedOff() {
        contextRunner.withPropertyValues("plog.report.scheduler.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ReportGenerationScheduler.class));
    }

    @Test
    void registersOnlyWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("plog.report.scheduler.enabled=true").run(context ->
                assertThat(context).hasSingleBean(ReportGenerationScheduler.class));
    }

    // 배치 전체가 못 도는 예외는 로그만 남기고 삼킨다 — 스케줄러 스레드가 죽으면 다음 회차도 안 돈다.
    @Test
    void swallowsBatchFailureSoTheScheduleKeepsRunning() {
        ReportBatchService batchService = mock(ReportBatchService.class);
        given(batchService.startDueReports()).willThrow(new IllegalStateException("조회 실패"));

        new ReportGenerationScheduler(batchService).startDueReports();

        verify(batchService).startDueReports();
    }

    @Configuration
    static class BatchServiceConfig {
        @Bean
        ReportBatchService reportBatchService() {
            ReportBatchService batchService = mock(ReportBatchService.class);
            given(batchService.startDueReports()).willReturn(ReportBatchResult.empty());
            return batchService;
        }
    }
}
