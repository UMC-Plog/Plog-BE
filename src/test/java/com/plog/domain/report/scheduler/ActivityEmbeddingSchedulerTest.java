package com.plog.domain.report.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.report.service.ActivityEmbeddingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ActivityEmbeddingSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActivityEmbeddingScheduler.class))
            .withUserConfiguration(ServiceConfig.class);

    // Gemini API 키 없이 로컬/CI에서 자동으로 켜지면 Stub 임베딩이 대량으로 채워진다
    // → 기본값은 반드시 꺼짐이어야 한다.
    @Test
    @DisplayName("기본값은 비활성화")
    void isDisabledByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(ActivityEmbeddingScheduler.class));
    }

    @Test
    @DisplayName("명시적으로 꺼두면 빈이 등록되지 않는다")
    void isDisabledWhenExplicitlyTurnedOff() {
        contextRunner.withPropertyValues("plog.report.embedding.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(ActivityEmbeddingScheduler.class));
    }

    @Test
    @DisplayName("명시적으로 켜야만 빈이 등록된다")
    void registersOnlyWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("plog.report.embedding.enabled=true").run(context ->
                assertThat(context).hasSingleBean(ActivityEmbeddingScheduler.class));
    }

    @Test
    @DisplayName("스케줄러는 ActivityEmbeddingService.embedBatch()에 위임한다")
    void delegatesToEmbedBatch() {
        ActivityEmbeddingService service = mock(ActivityEmbeddingService.class);
        given(service.embedBatch()).willReturn(5);

        new ActivityEmbeddingScheduler(service).embed();

        verify(service, times(1)).embedBatch();
    }

    // 선점(claim) 자체가 실패하는 등 배치 전체가 못 도는 예외는 로그만 남기고 삼킨다 —
    // 스케줄러 스레드가 죽으면 다음 회차도 안 돈다.
    @Test
    @DisplayName("배치 자체가 실패해도 예외를 삼켜 다음 스케줄을 살린다")
    void swallowsBatchFailureSoTheScheduleKeepsRunning() {
        ActivityEmbeddingService service = mock(ActivityEmbeddingService.class);
        given(service.embedBatch()).willThrow(new IllegalStateException("선점 실패"));

        assertThatCode(() -> new ActivityEmbeddingScheduler(service).embed())
                .doesNotThrowAnyException();

        verify(service, times(1)).embedBatch();
    }

    @Configuration
    static class ServiceConfig {
        @Bean
        ActivityEmbeddingService activityEmbeddingService() {
            return mock(ActivityEmbeddingService.class);
        }
    }
}