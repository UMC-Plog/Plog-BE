package com.plog.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.user.dto.response.SocialLoginResponse;
import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.service.SocialLoginService;
import com.plog.domain.user.service.SocialSignupService;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaCookieFactory;
import com.plog.global.security.jwt.MediaTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.ResponseCookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SocialAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class SocialAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SocialLoginService socialLoginService;
    @MockitoBean
    private SocialSignupService socialSignupService;
    // JwtAuthenticationFilter 가 Filter 라 슬라이스에 포함되고 JwtProvider 를 요구한다.
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MediaTokenProvider mediaTokenProvider;
    // 토큰을 내려주는 응답(LOGIN, 가입 완료)에 미디어 쿠키가 함께 실린다.
    @MockitoBean
    private MediaCookieFactory mediaCookieFactory;
    // PlogApplication 의 @EnableJpaAuditing 이 슬라이스에서도 매핑 컨텍스트를 찾는다.
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("기존 유저는 AUTH011과 토큰을 받는다")
    void loginReturnsTokens() throws Exception {
        given(socialLoginService.login(eq(ProviderType.KAKAO), eq("code")))
                .willReturn(SocialLoginResponse.login(new TokenResponse("access", "refresh")));
        given(mediaCookieFactory.issue("access")).willReturn(mediaCookie());

        mockMvc.perform(post("/api/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH011"))
                .andExpect(jsonPath("$.result.status").value("LOGIN"))
                .andExpect(jsonPath("$.result.accessToken").value("access"))
                // 토큰을 내려주는 응답에는 미디어 쿠키가 반드시 실려야 한다. 빠지면 이 경로로
                // 로그인한 사용자만 채팅 이미지가 깨진다.
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("plog_media=")));
    }

    @Test
    @DisplayName("신규 유저는 AUTH012와 티켓을 받는다")
    void loginReturnsTicket() throws Exception {
        given(socialLoginService.login(eq(ProviderType.GOOGLE), eq("code")))
                .willReturn(SocialLoginResponse.signupRequired("raw-ticket", "user@gmail.com"));

        mockMvc.perform(post("/api/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH012"))
                .andExpect(jsonPath("$.result.ticket").value("raw-ticket"))
                .andExpect(jsonPath("$.result.accessToken").doesNotExist())
                // SIGNUP_REQUIRED 에는 토큰이 없으므로 쿠키도 심지 않는다.
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    @DisplayName("알 수 없는 provider는 AUTH019")
    void rejectsUnknownProvider() throws Exception {
        mockMvc.perform(post("/api/auth/oauth/naver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"code\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH019"));
    }

    @Test
    @DisplayName("code가 비면 400으로 끊는다")
    void rejectsBlankCode() throws Exception {
        mockMvc.perform(post("/api/auth/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가입 완료는 201 AUTH013")
    void signupReturnsCreated() throws Exception {
        given(socialSignupService.signup(any())).willReturn(new TokenResponse("access", "refresh"));
        given(mediaCookieFactory.issue("access")).willReturn(mediaCookie());

        String body = """
                {"ticket":"raw-ticket","name":"홍길동","nickname":"조이","profilePreset":"OTTER",
                 "agreements":[{"agreementType":"SERVICE_TERMS","agreed":true},
                               {"agreementType":"PRIVACY","agreed":true},
                               {"agreementType":"EXTERNAL_DATA","agreed":true}]}
                """;

        mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("AUTH013"))
                .andExpect(jsonPath("$.result.accessToken").value("access"))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("plog_media=")));
    }

    @Test
    @DisplayName("만료 티켓은 AUTH023")
    void signupRejectsExpiredTicket() throws Exception {
        given(socialSignupService.signup(any()))
                .willThrow(new ApiException(AuthErrorCode.SOCIAL_TICKET_EXPIRED));

        String body = """
                {"ticket":"raw-ticket","name":"홍길동","nickname":"조이",
                 "agreements":[{"agreementType":"SERVICE_TERMS","agreed":true}]}
                """;

        mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH023"));
    }

    private ResponseCookie mediaCookie() {
        return ResponseCookie.from("plog_media", "media-token")
                .path("/api/chat-attachments").build();
    }
}
