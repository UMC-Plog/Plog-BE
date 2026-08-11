package com.plog.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

class SchedulingConfigTest {

    @Test
    void schedulingIsEnabledByDefault() {
        try (AnnotationConfigApplicationContext context = contextWith(Map.of())) {
            assertThat(context.containsBean(
                    TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)).isTrue();
        }
    }

    @Test
    void schedulingCanBeDisabledForIntegrationTests() {
        try (AnnotationConfigApplicationContext context =
                     contextWith(Map.of("plog.scheduling.enabled", "false"))) {
            assertThat(context.containsBean(
                    TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)).isFalse();
        }
    }

    private AnnotationConfigApplicationContext contextWith(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", properties));
        context.register(SchedulingConfig.class);
        context.refresh();
        return context;
    }
}
