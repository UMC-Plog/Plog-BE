package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.plog.domain.user.config.KakaoOAuthProperties;
import com.plog.domain.user.entity.ProviderType;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoOAuthClientTest {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
    private static final KakaoOAuthProperties PROPERTIES =
            new KakaoOAuthProperties("rest-api-key", "secret", "http://localhost:5173/oauth/kakao");

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuthClient(PROPERTIES, builder);
    }

    private void givenToken() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"at\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("회원번호와 이메일을 SocialIdentity로 변환한다")
    void exchangeReturnsIdentity() {
        givenToken();
        server.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("""
                        {"id":123456789,
                         "kakao_account":{"email":"user@kakao.com",
                                          "is_email_valid":true,
                                          "is_email_verified":true}}
                        """, MediaType.APPLICATION_JSON));

        SocialIdentity identity = client.exchange("auth-code");

        assertThat(identity.provider()).isEqualTo(ProviderType.KAKAO);
        assertThat(identity.providerId()).isEqualTo("123456789");
        assertThat(identity.email()).isEqualTo("user@kakao.com");
        server.verify();
    }

    @Test
    @DisplayName("이메일이 없으면 SOCIAL_EMAIL_NOT_PROVIDED")
    void rejectsMissingEmail() {
        givenToken();
        server.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("{\"id\":1,\"kakao_account\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("auth-code"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_EMAIL_NOT_PROVIDED);
    }

    @Test
    @DisplayName("미검증 이메일은 SOCIAL_EMAIL_NOT_PROVIDED로 거부한다")
    void rejectsUnverifiedEmail() {
        givenToken();
        server.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("""
                        {"id":1,"kakao_account":{"email":"user@kakao.com",
                                                 "is_email_valid":true,
                                                 "is_email_verified":false}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("auth-code"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_EMAIL_NOT_PROVIDED);
    }

    @Test
    @DisplayName("토큰 교환 실패는 SOCIAL_AUTHORIZATION_FAILED로 변환한다")
    void mapsTokenFailure() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.exchange("auth-code"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_AUTHORIZATION_FAILED);
    }

    @Test
    @DisplayName("client-id가 비어 있으면 요청을 보내기 전에 설정 오류로 끊는다")
    void rejectsMissingConfiguration() {
        // mock 서버에 묶인 빌더를 그대로 쓴다. 새 RestClient.builder()를 만들면 require() 검사가
        // 사라지는 회귀가 났을 때 테스트가 실제 카카오 서버로 나가버린다.
        KakaoOAuthClient misconfigured =
                new KakaoOAuthClient(new KakaoOAuthProperties("", "secret", "uri"), builder);

        assertThatThrownBy(() -> misconfigured.exchange("auth-code"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_CONFIGURATION_ERROR);
        // 기대한 요청을 하나도 등록하지 않았으므로, 나간 요청이 있으면 여기서 걸린다.
        server.verify();
    }
}
