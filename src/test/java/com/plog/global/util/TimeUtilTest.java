package com.plog.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TimeUtilTest {

    @Test
    void mapsAKoreanCalendarDayStartOntoItsUtcStorageValue() {
        // KST 2026-07-21 00:00 == UTC 2026-07-20 15:00
        assertThat(TimeUtil.startOfDayUtc(LocalDate.of(2026, 7, 21)))
                .isEqualTo(LocalDateTime.of(2026, 7, 20, 15, 0));
    }

    @Test
    void keepsTheKoreanDayBoundaryExactlyTwentyFourHoursWide() {
        LocalDateTime start = TimeUtil.startOfDayUtc(LocalDate.of(2026, 7, 21));
        LocalDateTime endExclusive = TimeUtil.startOfDayUtc(LocalDate.of(2026, 7, 22));

        assertThat(java.time.Duration.between(start, endExclusive)).hasHours(24);
    }

    @Test
    void passesNullThroughWhenConvertingToInstant() {
        assertThat(TimeUtil.toInstant(null)).isNull();
    }
}
