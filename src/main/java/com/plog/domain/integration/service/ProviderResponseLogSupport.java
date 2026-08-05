package com.plog.domain.integration.service;

/** provider API 에러 응답 본문을 로그로 남기기 전, 제어 문자를 제거하고 길이를 제한한다. */
final class ProviderResponseLogSupport {

    private static final int MAX_LOGGED_BODY_LENGTH = 500;

    private ProviderResponseLogSupport() {
    }

    static String sanitizeForLog(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        String withoutControlCharacters = responseBody.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "?");
        return withoutControlCharacters.length() > MAX_LOGGED_BODY_LENGTH
                ? withoutControlCharacters.substring(0, MAX_LOGGED_BODY_LENGTH) + "...(truncated)"
                : withoutControlCharacters;
    }
}