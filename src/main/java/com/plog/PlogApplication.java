package com.plog;

import com.plog.global.util.TimeUtil;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
@SpringBootApplication
public class PlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlogApplication.class, args);
    }

    // 감사 컬럼을 UTC로 고정한다. 기본 제공자는 JVM 기본 타임존을 써서 환경마다 9시간 갈린다.
    @Bean
    DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(TimeUtil.nowUtc());
    }
}
