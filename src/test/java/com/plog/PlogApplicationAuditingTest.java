package com.plog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

class PlogApplicationAuditingTest {

    @Test
    void stampsAuditingTimestampsInUtc() {
        DateTimeProvider provider = new PlogApplication().utcDateTimeProvider();

        LocalDateTime stamped = LocalDateTime.from(provider.getNow().orElseThrow());

        assertThat(stamped).isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(5, ChronoUnit.SECONDS));
    }

    /**
     * 슬라이스 테스트(@DataJpaTest 등)는 일반 @Configuration 을 로드하지 않는다.
     * 감사 설정이 @SpringBootConfiguration 클래스를 떠나면 통합테스트에서 조용히 꺼지고
     * createdAt/updatedAt 이 null 이 되어 NOT NULL 제약에 걸린다.
     */
    @Test
    void keepsAuditingWiredOnTheSpringBootConfigurationClass() {
        EnableJpaAuditing auditing = PlogApplication.class.getAnnotation(EnableJpaAuditing.class);

        assertThat(auditing).isNotNull();
        assertThat(auditing.dateTimeProviderRef()).isEqualTo("utcDateTimeProvider");
    }

    @Test
    void exposesTheReferencedProviderAsABeanOnTheSameClass() throws Exception {
        Method beanMethod = PlogApplication.class.getDeclaredMethod("utcDateTimeProvider");

        assertThat(beanMethod.isAnnotationPresent(org.springframework.context.annotation.Bean.class)).isTrue();
        assertThat(beanMethod.getReturnType()).isEqualTo(DateTimeProvider.class);
    }
}
