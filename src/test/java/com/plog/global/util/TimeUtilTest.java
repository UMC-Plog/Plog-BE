package com.plog.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimeUtilTest {

    @Test
    void storesOnTheKoreanStandardTime() {
        assertThat(TimeUtil.STORAGE_ZONE).isEqualTo(ZoneOffset.ofHours(9));
    }

    // 저장 기준과 표시 기준이 같아져 변환이 항등이 된다. 두 기준이 다시 갈릴 때를 위해 경유는 유지한다.
    @Test
    void mapsAKoreanCalendarDayStartOntoItself() {
        assertThat(TimeUtil.startOfDay(LocalDate.of(2026, 7, 21)))
                .isEqualTo(LocalDateTime.of(2026, 7, 21, 0, 0));
    }

    @Test
    void keepsTheKoreanDayBoundaryExactlyTwentyFourHoursWide() {
        LocalDateTime start = TimeUtil.startOfDay(LocalDate.of(2026, 7, 21));
        LocalDateTime endExclusive = TimeUtil.startOfDay(LocalDate.of(2026, 7, 22));

        assertThat(java.time.Duration.between(start, endExclusive)).hasHours(24);
    }

    // 저장값에 +09:00 을 실어 절대시각으로 바꾼다. 응답이 가리키는 순간은 전환 전과 같아야 한다.
    @Test
    void convertsAStoredValueIntoTheSameAbsoluteInstantAsBefore() {
        LocalDateTime storedInKst = LocalDateTime.of(2026, 7, 21, 12, 0);

        assertThat(TimeUtil.toInstant(storedInKst)).isEqualTo(Instant.parse("2026-07-21T03:00:00Z"));
    }

    @Test
    void passesNullThroughWhenConvertingToInstant() {
        assertThat(TimeUtil.toInstant(null)).isNull();
    }
}
