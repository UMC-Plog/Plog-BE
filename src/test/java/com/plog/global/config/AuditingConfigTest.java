package com.plog.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

class AuditingConfigTest {

    @Test
    void stampsAuditingTimestampsInUtc() {
        DateTimeProvider provider = new AuditingConfig().auditingDateTimeProvider();

        LocalDateTime stamped = LocalDateTime.from(provider.getNow().orElseThrow());

        assertThat(stamped).isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(5, ChronoUnit.SECONDS));
    }
}
