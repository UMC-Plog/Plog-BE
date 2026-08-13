package com.plog.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 설정 값이 실제 레지스트리 생성으로 이어지는지 본다.
 * 토큰이 없는 로컬·CI 에서 push 를 시도하지 않는 것이 이 테스트의 핵심 보장이다.
 */
class OtlpMetricsExportTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MetricsAutoConfiguration.class,
                    OtlpMetricsExportAutoConfiguration.class));

    @Test
    void registryIsAbsentWhenExportIsDisabled() {
        runner.withPropertyValues("management.otlp.metrics.export.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class));
    }

    @Test
    void registryIsCreatedWhenExportIsEnabled() {
        runner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=https://example.invalid/otlp/v1/metrics")
                .run(context -> assertThat(context).hasSingleBean(OtlpMeterRegistry.class));
    }

    @Test
    void enabledRegistryDoesNotBreakContextStartupWithUnreachableEndpoint() {
        // 토큰 만료·오설정으로 엔드포인트에 못 붙어도 기동은 성공해야 한다.
        // cd.yml 이 이 값을 optional 로 두기 때문에 실제로 발생할 수 있는 상황이다.
        runner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=https://unreachable.invalid/otlp/v1/metrics",
                        "management.otlp.metrics.export.headers.Authorization=Basic wrong-token")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
