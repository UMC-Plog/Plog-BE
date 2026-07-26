package com.plog.domain.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.domain.user.config.KakaoOAuthProperties;
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
 * 카카오는 동의항목 설정으로 수집 범위가 정해진다. 이 서비스는 account_email 만 받는다 —
 * 닉네임·프로필 사진은 약관에 없는 항목이고, 아바타는 프리셋만 쓰므로 받아도 버린다.
 */
@Slf4j
@Component
public class KakaoOAuthClient implements SocialOAuthClient {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final KakaoOAuthProperties properties;
    private final RestClient restClient;

    @Autowired
    public KakaoOAuthClient(KakaoOAuthProperties properties) {
        this(properties, SocialRestClientFactory.builder());
    }

    /** 테스트에서 MockRestServiceServer 를 물리기 위한 생성자. */
    KakaoOAuthClient(KakaoOAuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public ProviderType provider() {
        return ProviderType.KAKAO;
    }

    @Override
    public SocialIdentity exchange(String code) {
        String accessToken = requiredText(requestToken(code), "access_token");
        JsonNode profile = requestProfile(accessToken);

        String providerId = requiredText(profile, "id");
        JsonNode account = profile.path("kakao_account");
        String email = account.path("email").asText(null);
        // is_email_valid: 카카오가 형식·중복을 확인한 값인지, is_email_verified: 소유 확인이 끝났는지.
        // 둘 중 하나라도 false면 신뢰할 수 없다.
        boolean usable = account.path("is_email_valid").asBoolean(false)
                && account.path("is_email_verified").asBoolean(false);
        if (email == null || email.isBlank() || !usable) {
            throw new ApiException(AuthErrorCode.SOCIAL_EMAIL_NOT_PROVIDED);
        }
        return new SocialIdentity(ProviderType.KAKAO, providerId, email);
    }

    private JsonNode requestToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", require(properties.clientId()));
        body.add("client_secret", require(properties.clientSecret()));
        // provider는 인가 시점과 교환 시점의 redirect_uri가 같은지 대조한다. 프론트가 쓰는 값과 반드시 일치해야 한다.
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
            // 응답 예외 메시지에 카카오의 error/error_description(KOE006 등)이 들어 있다. 원인 규명의 유일한 단서라
            // 반드시 남긴다. 요청 바디(code, client_secret)는 이 예외에 포함되지 않는다.
            log.warn("카카오 토큰 교환 실패", e);
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
            log.warn("카카오 프로필 조회 실패", e);
            throw new ApiException(AuthErrorCode.SOCIAL_AUTHORIZATION_FAILED, e);
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            // 값 자체는 남기지 않는다(토큰일 수 있다). 어떤 필드가 비었는지만으로 충분히 추적된다.
            log.warn("카카오 응답에 필수 필드가 없습니다: {}", field);
            throw new ApiException(AuthErrorCode.SOCIAL_AUTHORIZATION_FAILED);
        }
        return value;
    }

    private String require(String value) {
        if (value == null || value.isBlank()) {
            // 설정 누락은 배포 사고다. 500으로 나가지만 GlobalExceptionHandler는 ApiException을 로깅하지 않으므로
            // 여기서 남기지 않으면 로그가 완전히 비어 있게 된다.
            log.error("카카오 OAuth 설정(plog.oauth.kakao.*)이 비어 있습니다. 배포 환경변수를 확인하세요.");
            throw new ApiException(AuthErrorCode.SOCIAL_PROVIDER_CONFIGURATION_ERROR);
        }
        return value;
    }
}
