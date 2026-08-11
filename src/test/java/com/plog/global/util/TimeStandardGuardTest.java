package com.plog.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 시각 기준은 TimeUtil 한 곳에서만 정의한다.
 * <p>
 * 인자 없는 now() 는 JVM 기본 타임존을 따르므로 로컬과 CI·운영에서 다르게 동작한다.
 * 오프셋을 코드에 직접 박으면 기준이 여러 곳으로 흩어져, 한 곳만 고치고 나머지를 놓치는 사고가 난다.
 * 실제로 이 가드를 도입하기 전 12곳이 그렇게 흩어져 있었다.
 * <p>
 * Instant.now() 는 절대시각이라 타임존과 무관하므로 허용한다.
 */
class TimeStandardGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    private static final String TIME_UTIL_FILE = "TimeUtil.java";

    private static final Map<Pattern, String> FORBIDDEN = forbiddenRules();

    private static Map<Pattern, String> forbiddenRules() {
        Map<Pattern, String> rules = new LinkedHashMap<>();
        rules.put(Pattern.compile("\\bLocalDate\\.now\\("), "LocalDate.now(...) 대신 TimeUtil.today() 를 쓴다");
        rules.put(Pattern.compile("\\bLocalDateTime\\.now\\("), "LocalDateTime.now(...) 대신 TimeUtil.now() 를 쓴다");
        rules.put(Pattern.compile("\\bZoneOffset\\.UTC\\b"), "ZoneOffset.UTC 대신 TimeUtil.STORAGE_ZONE 을 쓴다");
        rules.put(Pattern.compile("\\bZoneId\\.of\\("), "ZoneId.of(...) 대신 TimeUtil.DISPLAY_ZONE 을 쓴다");
        return rules;
    }

    @Test
    void definesTheTimeStandardOnlyInsideTimeUtil() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(MAIN_SOURCES)) {
            sources.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals(TIME_UTIL_FILE))
                    .forEach(path -> collectViolations(path, violations));
        }

        assertThat(violations)
                .describedAs("시간 기준은 TimeUtil 에서만 정의한다")
                .isEmpty();
    }

    private void collectViolations(Path path, List<String> violations) {
        List<String> lines = readLines(path);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            for (Map.Entry<Pattern, String> rule : FORBIDDEN.entrySet()) {
                if (rule.getKey().matcher(line).find()) {
                    violations.add(path + ":" + (index + 1) + " — " + rule.getValue());
                }
            }
        }
    }

    // build.gradle 의 user.timezone 설정이 실제로 먹는지 확인한다. 이게 풀리면 CI 와 로컬이 갈린다.
    @Test
    void runsTestsOnTheKoreanTimezone() {
        assertThat(java.util.TimeZone.getDefault().toZoneId())
                .isEqualTo(java.time.ZoneId.of("Asia/Seoul"));
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("소스를 읽지 못했습니다: " + path, e);
        }
    }
}
