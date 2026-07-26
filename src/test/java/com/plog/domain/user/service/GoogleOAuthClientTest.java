package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.plog.domain.user.config.GoogleOAuthProperties;
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

class GoogleOAuthClientTest {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final GoogleOAuthProperties PROPERTIES =
            new GoogleOAuthProperties("client-id", "secret", "http://localhost:5173/oauth/google");

    private MockRestServiceServer server;
    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleOAuthClient(PROPERTIES, builder);
    }

    private void givenToken() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"at\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("sub와 email을 SocialIdentity로 변환한다")
    void exchangeReturnsIdentity() {
        givenToken();
        server.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("""
                        {"sub":"110248495921238","email":"user@gmail.com","email_verified":true}
                        """, MediaType.APPLICATION_JSON));

        SocialIdentity identity = client.exchange("auth-code");

        assertThat(identity.provider()).isEqualTo(ProviderType.GOOGLE);
        assertThat(identity.providerId()).isEqualTo("110248495921238");
        assertThat(identity.email()).isEqualTo("user@gmail.com");
        server.verify();
    }

    @Test
    @DisplayName("email_verified가 false면 SOCIAL_EMAIL_NOT_PROVIDED")
    void rejectsUnverifiedEmail() {
        givenToken();
        server.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("""
                        {"sub":"1","email":"user@gmail.com","email_verified":false}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("auth-code"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_EMAIL_NOT_PROVIDED);
    }

    @Test
    @DisplayName("프로필 조회 401은 SOCIAL_AUTHORIZATION_FAILED로 변환한다")
    void mapsProfileFailure() {
        givenToken();
        server.expect(requestTo(USER_INFO_URL)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.exchange("auth-code"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_AUTHORIZATION_FAILED);
    }
}
