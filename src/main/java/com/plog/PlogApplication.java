package com.plog;

import java.time.Clock;
import java.time.LocalDateTime;
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

    @Bean
    DateTimeProvider utcDateTimeProvider() {
        Clock clock = Clock.systemUTC();
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
