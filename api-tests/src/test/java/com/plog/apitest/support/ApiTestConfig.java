package com.plog.apitest.support;

public record ApiTestConfig(String baseUrl, String email, String password, long projectId) {

    public static ApiTestConfig fromEnvironment() {
        return new ApiTestConfig(
                baseUrlFromEnvironment(),
                requiredEnvironment("PLOG_TEST_EMAIL"),
                requiredEnvironment("PLOG_TEST_PASSWORD"),
                requiredLongEnvironment("PLOG_PROJECT_ID"));
    }

    public static String baseUrlFromEnvironment() {
        return environment("PLOG_API_URL", "http://localhost:8080");
    }

    public static String optionalEnvironment(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String environment(String name, String defaultValue) {
        String value = optionalEnvironment(name);
        return value == null ? defaultValue : value;
    }

    private static long requiredLongEnvironment(String name) {
        return Long.parseLong(requiredEnvironment(name));
    }

    private static String requiredEnvironment(String name) {
        String value = optionalEnvironment(name);
        if (value == null) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }
}
