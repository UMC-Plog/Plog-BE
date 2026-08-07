package com.plog.domain.report.service;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 리포트 파이프라인 1단계(Rule 기반 정제) 규칙 엔진.
 * <p>
 * {@link com.plog.domain.report.entity.ReportActivityLog#getContent()} 원문은 절대 수정하지 않는다 —
 * 이 클래스는 순수 함수로 정제 결과만 계산해서 돌려주고, 원본에 반영하는 건 호출부의 책임이다.
 * 업무 관련 문장은 삭제 대상이 아니다: 여기서 노이즈로 판정하는 건 순수 잡담성 텍스트
 * (단독 감탄사, 순수 웃음, 빈 문자열)뿐이다.
 */
public final class ActivityContentRefiner {

    private ActivityContentRefiner() {
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    // ㅋ 또는 ㅎ이 3회 이상 반복되면 2글자로 축약 (웃음 정규화). 균일한 반복만 다루고
    // "ㅋㅎㅋㅎ" 같은 섞인 패턴은 과도하게 손대지 않는다 — 흔치 않고 의미 왜곡 위험이 크다.
    private static final Pattern REPEATED_LAUGH = Pattern.compile("([ㅋㅎ])\\1{2,}");

    // 순수 웃음(ㅋ/ㅎ)만으로 이뤄진 메시지인지 — 정규화 이후 이것만 남으면 단독 감탄사와 같은 취급.
    private static final Pattern LAUGH_ONLY = Pattern.compile("[ㅋㅎ]+");

    // 대표적인 이모지 유니코드 블록. 국기(1F1E6-1F1FF), 딩뱃/기호(2600-27BF), 확장 픽토그램(1F300-1FAFF),
    // 변형 선택자(FE0F)·ZWJ(200D)까지 포함해 조합 이모지도 걷어낸다.
    private static final Pattern EMOJI = Pattern.compile(
            "[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{1F1E6}-\\x{1F1FF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}]+");

    private static final String AGREE_TOKEN = "ㅇㅇ";
    private static final String AGREE_REPLACEMENT = "동의";
    private static final String PROCEED_TOKEN = "ㄱㄱ";
    private static final String PROCEED_REPLACEMENT = "진행";

    // 단독으로 왔을 때 그 자체로는 아무 정보도 담지 않는 감탄사. 필요 시 계속 확장한다.
    private static final Set<String> STANDALONE_INTERJECTIONS = Set.of(
            "아", "오", "음", "흠", "어", "와", "헐", "엥", "앗", "오호", "허", "엇", "윽", "쳇", "흥", "쩝", "휴", "하", "호", "헉"
    );

    public static RefinedContent refine(String rawContent) {
        if (rawContent == null) {
            // 텍스트 필드 자체가 없는 이벤트(상태변경 등) — 노이즈가 아니라 정제 대상이 아닌 것.
            return new RefinedContent(null, false);
        }

        // null과 달리, 공백뿐인 실제 메시지는 아무 정보도 없으므로 노이즈로 본다.
        String normalized = normalizeWhitespace(rawContent);
        if (normalized.isEmpty()) {
            return new RefinedContent(null, true);
        }

        normalized = EMOJI.matcher(normalized).replaceAll("");
        normalized = normalizeWhitespace(normalized);
        normalized = normalizeLaughter(normalized);
        normalized = normalizeWhitespace(normalized);

        if (normalized.isEmpty()) {
            return new RefinedContent(null, true);
        }
        if (AGREE_TOKEN.equals(normalized)) {
            return new RefinedContent(AGREE_REPLACEMENT, false);
        }
        if (PROCEED_TOKEN.equals(normalized)) {
            return new RefinedContent(PROCEED_REPLACEMENT, false);
        }
        if (STANDALONE_INTERJECTIONS.contains(normalized) || LAUGH_ONLY.matcher(normalized).matches()) {
            return new RefinedContent(null, true);
        }

        return new RefinedContent(normalized, false);
    }

    private static String normalizeWhitespace(String text) {
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }

    private static String normalizeLaughter(String text) {
        Matcher matcher = REPEATED_LAUGH.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1).repeat(2)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}