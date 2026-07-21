package com.plog.global.config;

import com.plog.global.util.TimeUtil;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 감사 컬럼(createdAt/updatedAt)을 UTC로 고정한다.
 * <p>
 * 기본 제공자는 JVM 기본 타임존을 쓰기 때문에 실행 환경(로컬 KST / 컨테이너 UTC)에 따라
 * 같은 코드가 9시간 다른 값을 남긴다. 컬럼이 timestamp without time zone 이라 그 차이가
 * 데이터에 흔적 없이 섞이므로, 제공자를 명시해 저장 기준을 코드로 고정한다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class AuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(TimeUtil.nowUtc());
    }
}
