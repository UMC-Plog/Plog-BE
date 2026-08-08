package com.plog.domain.report.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.report.service.ActivityRefinementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ActivityRefinementSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActivityRefinementScheduler.class))
            .withUserConfiguration(ServiceConfig.class);

    // 정제 규칙이 운영 데이터로 검증되기 전에 켜지면 대량의 noiseFiltered가 잘못 확정될 수 있다
    // → 기본값은 반드시 꺼짐이어야 한다.
    @Test
    @DisplayName("기본값은 비활성화")
    void isDisabledByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(ActivityRefinementScheduler.class));
    }

    @Test
    @DisplayName("명시적으로 꺼두면 빈이 등록되지 않는다")
    void isDisabledWhenExplicitlyTurnedOff() {
        contextRunner.withPropertyValues("plog.report.refinement.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ActivityRefinementScheduler.class));
    }

    @Test
    @DisplayName("명시적으로 켜야만 빈이 등록된다")
    void registersOnlyWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("plog.report.refinement.enabled=true").run(context ->
                assertThat(context).hasSingleBean(ActivityRefinementScheduler.class));
    }

    @Test
    @DisplayName("스케줄러는 ActivityRefinementService.refineNoiseBatch()에 위임한다")
    void delegatesToRefineNoiseBatch() {
        ActivityRefinementService service = mock(ActivityRefinementService.class);
        given(service.refineNoiseBatch()).willReturn(3);

        new ActivityRefinementScheduler(service).refine();

        verify(service, times(1)).refineNoiseBatch();
    }

    // 대상 조회 실패처럼 배치 전체가 못 도는 예외는 로그만 남기고 삼킨다 —
    // 스케줄러 스레드가 죽으면 다음 회차도 안 돈다.
    @Test
    @DisplayName("배치 자체가 실패해도 예외를 삼켜 다음 스케줄을 살린다")
    void swallowsBatchFailureSoTheScheduleKeepsRunning() {
        ActivityRefinementService service = mock(ActivityRefinementService.class);
        given(service.refineNoiseBatch()).willThrow(new IllegalStateException("조회 실패"));

        assertThatCode(() -> new ActivityRefinementScheduler(service).refine())
                .doesNotThrowAnyException();

        verify(service, times(1)).refineNoiseBatch();
    }

    @Configuration
    static class ServiceConfig {
        @Bean
        ActivityRefinementService activityRefinementService() {
            return mock(ActivityRefinementService.class);
        }
    }
}