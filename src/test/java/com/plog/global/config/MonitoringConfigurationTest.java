package com.plog.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 모니터링 설정은 값이 하나만 뒤집혀도 조용히 망가진다.
 * - 로컬/CI 기본값이 true 가 되면 토큰 없는 환경에서 매분 push 를 시도한다.
 * - 관리 포트 분리가 풀리면 actuator 가 nginx 뒤 8080 에 노출된다.
 * 컨텍스트를 띄우지 않고 YAML 을 직접 읽어 두 가지를 고정한다.
 */
class MonitoringConfigurationTest {

    @Test
    void otlpExportIsDisabledByDefault() {
        assertThat(property("application.yaml", "management.otlp.metrics.export.enabled"))
                .isEqualTo(false);
    }

    @Test
    void onlyHealthEndpointIsExposed() {
        assertThat(property("application.yaml", "management.endpoints.web.exposure.include"))
                .isEqualTo("health");
    }

    @Test
    void healthDetailsAreNeverShown() {
        assertThat(property("application.yaml", "management.endpoint.health.show-details"))
                .isEqualTo("never");
    }

    @Test
    void otlpExportIsEnabledInProduction() {
        assertThat(property("application-prod.yaml", "management.otlp.metrics.export.enabled"))
                .isEqualTo(true);
    }

    @Test
    void productionUrlComesFromEnvironmentWithoutFallback() {
        assertThat(property("application-prod.yaml", "management.otlp.metrics.export.url"))
                .isEqualTo("${GRAFANA_OTLP_URL}");
    }

    @Test
    void productionAuthorizationHeaderUsesBasicScheme() {
        assertThat(property("application-prod.yaml",
                "management.otlp.metrics.export.headers.Authorization"))
                .isEqualTo("Basic ${GRAFANA_OTLP_AUTH}");
    }

    @Test
    void managementPortIsSeparatedInProduction() {
        assertThat(property("application-prod.yaml", "management.server.port"))
                .isEqualTo(8081);
    }

    private Object property(String fileName, String key) {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load(fileName, new ClassPathResource(fileName));
            return sources.stream()
                    .filter(source -> source.containsProperty(key))
                    .map(source -> source.getProperty(key))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new IllegalStateException(fileName + " 를 읽지 못했습니다", e);
        }
    }
}
