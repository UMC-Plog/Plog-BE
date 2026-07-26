package com.plog.domain.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.user.config.GoogleOAuthProperties;
import com.plog.domain.user.entity.ProviderType;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 구글은 openid/email 스코프만 요청한다(프로필·사진 불필요 — 아바타는 프리셋만 쓴다).
 * id_token 을 직접 파싱하지 않고 userinfo 엔드포인트를 조회한다 —
 * GoogleIntegrationService 가 이미 같은 방식이라 JWT 검증 코드를 새로 들일 이유가 없다.
 */
@Slf4j
@Component
public class GoogleOAuthClient implements SocialOAuthClient {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    private final GoogleOAuthProperties properties;
    private final RestClient restClient;

    @Autowired
    public GoogleOAuthClient(GoogleOAuthProperties properties) {
        this(properties, SocialRestClientFactory.builder());
    }

    /** 테스트에서 MockRestServiceServer 를 물리기 위한 생성자. */
    GoogleOAuthClient(GoogleOAuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public ProviderType provider() {
        return ProviderType.GOOGLE;
    }

    @Override
    public SocialIdentity exchange(String code) {
        String accessToken = requiredText(requestToken(code), "access_token");
        JsonNode profile = requestProfile(accessToken);

        String providerId = requiredText(profile, "sub");
        String email = profile.path("email").asText(null);
        if (email == null || email.isBlank() || !profile.path("email_verified").asBoolean(false)) {
            throw new ApiException(AuthErrorCode.SOCIAL_EMAIL_NOT_PROVIDED);
        }
        return new SocialIdentity(ProviderType.GOOGLE, providerId, email);
    }

    private JsonNode requestToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", require(properties.clientId()));
        body.add("client_secret", require(properties.clientSecret()));
        body.add("redirect_uri", require(properties.redirectUri()));
        body.add("code", code);
        try {
            return restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // 응답 예외 메시지에 구글의 error(invalid_grant, redirect_uri_mismatch 등)가 들어 있다.
            // 요청 바디(code, client_secret)는 이 예외에 포함되지 않는다.
            log.warn("구글 토큰 교환 실패", e);
            throw new ApiException(AuthErrorCode.SOCIAL_AUTHORIZATION_FAILED, e);
        }
    }

    private JsonNode requestProfile(String accessToken) {
        try {
            return restClient.get()
                    .uri(USER_INFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("구글 프로필 조회 실패", e);
            throw new ApiException(AuthErrorCode.SOCIAL_AUTHORIZATION_FAILED, e);
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            // 값 자체는 남기지 않는다(토큰일 수 있다). 어떤 필드가 비었는지만으로 충분히 추적된다.
            log.warn("구글 응답에 필수 필드가 없습니다: {}", field);
            throw new ApiException(AuthErrorCode.SOCIAL_AUTHORIZATION_FAILED);
        }
        return value;
    }

    private String require(String value) {
        if (value == null || value.isBlank()) {
            // 설정 누락은 배포 사고다. 500으로 나가지만 GlobalExceptionHandler는 ApiException을 로깅하지 않으므로
            // 여기서 남기지 않으면 로그가 완전히 비어 있게 된다.
            log.error("구글 OAuth 설정(plog.oauth.google.*)이 비어 있습니다. 배포 환경변수를 확인하세요.");
            throw new ApiException(AuthErrorCode.SOCIAL_PROVIDER_CONFIGURATION_ERROR);
        }
        return value;
    }
}
