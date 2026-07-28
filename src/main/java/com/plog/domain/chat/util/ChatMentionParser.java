package com.plog.domain.chat.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채팅 메시지 본문에서 "@닉네임" 형태의 멘션 후보 토큰을 추출한다.
 * 실제 존재하는 프로젝트 멤버와의 매칭은 이 클래스의 책임이 아니다(순수 파싱만 담당).
 */
public final class ChatMentionParser {

    // '@' 뒤에 공백이 아닌 문자가 1개 이상 오는 경우만 후보로 인정한다.
    // "@ " 처럼 '@' 뒤에 공백/문자열 끝이 오면 매치되지 않아 "@만 입력된 경우"는 자동으로 무시된다.
    private static final Pattern MENTION_TOKEN_PATTERN = Pattern.compile("@(\\S+)");

    // 닉네임 뒤에 붙은 문장부호를 제거하기 위한 트레일링 문자 집합.
    private static final String TRAILING_PUNCTUATION = ".,!?)]}:;'\"“”‘’「」『』>~";

    private ChatMentionParser() {
    }

    public static Set<String> extractNicknameCandidates(String message) {
        if (message == null || message.isBlank()) {
            return Set.of();
        }
        Set<String> candidates = new LinkedHashSet<>();
        Matcher matcher = MENTION_TOKEN_PATTERN.matcher(message);
        while (matcher.find()) {
            String trimmed = stripTrailingPunctuation(matcher.group(1));
            if (!trimmed.isBlank()) {
                candidates.add(trimmed);
            }
        }
        return candidates;
    }

    private static String stripTrailingPunctuation(String token) {
        int end = token.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(token.charAt(end - 1)) >= 0) {
            end--;
        }
        return token.substring(0, end);
    }
}