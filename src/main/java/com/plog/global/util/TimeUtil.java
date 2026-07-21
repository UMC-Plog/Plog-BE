package com.plog.global.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 시각 표준: 저장·연산은 UTC로 고정하고, KST 변환은 표현 계층(클라이언트)이 담당한다.
 * <p>
 * 엔티티가 LocalDateTime(= timestamp without time zone)을 쓰기 때문에 값 자체에는 오프셋이 없다.
 * 그래서 "그 값이 UTC"라는 약속을 코드 한 곳에서만 표현하도록 이 클래스를 경유한다.
 * 응답으로 나갈 때는 Instant로 바꿔 오프셋(Z)을 실어 보낸다 — 클라이언트가 서버 타임존을 추측하지 않도록.
 */
public final class TimeUtil {

    public static final ZoneOffset STORAGE_ZONE = ZoneOffset.UTC;

    /**
     * 사용자가 보고 입력하는 기준 타임존. 국내 전용 서비스라 KST 고정이다.
     * 저장에는 쓰지 않는다 — 클라이언트가 보낸 "날짜"를 저장 기준으로 옮길 때만 쓴다.
     */
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private TimeUtil() {
    }

    /** 저장·비교에 쓰는 현재 시각. */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(STORAGE_ZONE);
    }

    /** 날짜 경계 판정에 쓰는 오늘. */
    public static LocalDate todayUtc() {
        return LocalDate.now(STORAGE_ZONE);
    }

    /** 저장값(UTC 기준 LocalDateTime)을 응답용 절대시각으로 변환. null은 그대로 통과. */
    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(STORAGE_ZONE);
    }

    /**
     * 사용자가 보낸 날짜(KST 달력 기준)의 하루 시작을 저장 기준(UTC) 값으로 변환한다.
     * <p>
     * 날짜 필터를 {@code date.atStartOfDay()} 로 그냥 쓰면 UTC 달력 하루가 잡혀서,
     * 한국 사용자가 기대하는 구간과 9시간 어긋난다. 기간 조회의 경계는 이 메서드를 경유할 것.
     */
    public static LocalDateTime startOfDayUtc(LocalDate displayDate) {
        return displayDate.atStartOfDay(DISPLAY_ZONE)
                .withZoneSameInstant(STORAGE_ZONE)
                .toLocalDateTime();
    }
}
