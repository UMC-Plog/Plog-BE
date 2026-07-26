package com.plog.domain.user.service;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 소셜 로그인 provider 호출용 RestClient 팩토리. 타임아웃 기본값(무제한)을 쓰면
 * provider가 늘어질 때 요청 스레드가 그대로 묶이므로 명시적으로 고정한다.
 * <p>
 * integration 도메인에 같은 값을 쓰는 자체 팩토리가 따로 있다(ProviderRestClientFactory).
 * 도메인마다 자기 팩토리를 소유하는 구조를 유지해 소셜 로그인 변경이 연동 코드에 번지지 않게 했다 —
 * 대신 타임아웃을 조정할 때는 두 곳을 함께 봐야 한다.
 */
final class SocialRestClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private SocialRestClientFactory() {
    }

    /** 빌더 단계를 노출한다 — 테스트가 MockRestServiceServer 를 물릴 수 있어야 한다. */
    static RestClient.Builder builder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
