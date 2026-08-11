package com.plog.global.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 시각 표준: 저장·연산·표시를 모두 KST로 맞춘다. 국내 전용 서비스라 기준을 나눌 이유가 없다.
 * <p>
 * 엔티티가 LocalDateTime(= timestamp without time zone)을 쓰기 때문에 값 자체에는 오프셋이 없다.
 * 그래서 "그 값이 KST"라는 약속을 코드 한 곳에서만 표현하도록 이 클래스를 경유한다.
 * 응답으로 나갈 때는 Instant로 바꿔 오프셋을 실어 보낸다 — 클라이언트가 서버 타임존을 추측하지 않도록.
 */
public final class TimeUtil {

    /** 저장·비교의 기준. KST는 서머타임이 없어 고정 오프셋으로 못박는다. */
    public static final ZoneOffset STORAGE_ZONE = ZoneOffset.ofHours(9);

    /**
     * 사용자가 보고 입력하는 기준 타임존. 저장 기준과 값은 같지만 역할이 달라 분리해 둔다.
     */
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private TimeUtil() {
    }

    /** 저장·비교에 쓰는 현재 시각. */
    public static LocalDateTime now() {
        return LocalDateTime.now(STORAGE_ZONE);
    }

    /** 날짜 경계 판정에 쓰는 오늘. */
    public static LocalDate today() {
        return LocalDate.now(STORAGE_ZONE);
    }

    /** 저장값(KST 기준 LocalDateTime)을 응답용 절대시각으로 변환. null은 그대로 통과. */
    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(STORAGE_ZONE);
    }

    /**
     * 사용자가 보낸 날짜(KST 달력 기준)의 하루 시작을 저장 기준 값으로 변환한다.
     * <p>
     * 저장 기준과 표시 기준이 같아져 지금은 항등 변환이다. 그래도 기간 조회의 경계는 이 메서드를
     * 경유할 것 — 두 기준이 다시 갈릴 때 호출부를 다시 찾지 않아도 되게 한다.
     */
    public static LocalDateTime startOfDay(LocalDate displayDate) {
        return displayDate.atStartOfDay(DISPLAY_ZONE)
                .withZoneSameInstant(STORAGE_ZONE)
                .toLocalDateTime();
    }
}
