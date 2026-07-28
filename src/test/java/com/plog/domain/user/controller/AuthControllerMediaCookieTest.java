package com.plog.domain.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.service.AuthService;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaCookieFactory;
import com.plog.global.security.jwt.MediaTokenProvider;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerMediaCookieTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private MediaCookieFactory mediaCookieFactory;

    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MediaTokenProvider mediaTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void 로그인_응답에_미디어_쿠키가_실린다() throws Exception {
        given(authService.login(anyString(), anyString()))
                .willReturn(new TokenResponse("access-token", "refresh-token"));
        given(mediaCookieFactory.issue("access-token")).willReturn(issuedCookie());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Set-Cookie",
                        Matchers.containsString("plog_media=media-token")));
    }

    @Test
    void 재발급_응답에도_미디어_쿠키가_실린다() throws Exception {
        given(authService.reissue(anyString()))
                .willReturn(new TokenResponse("access-token", "refresh-token"));
        given(mediaCookieFactory.issue("access-token")).willReturn(issuedCookie());

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Set-Cookie",
                        Matchers.containsString("plog_media=media-token")));
    }

    @Test
    void 로그아웃_응답은_미디어_쿠키를_지운다() throws Exception {
        given(mediaCookieFactory.clear()).willReturn(
                ResponseCookie.from("plog_media", "")
                        .path("/api/chat-attachments").maxAge(0).build());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("Set-Cookie",
                        Matchers.containsString("Max-Age=0")));
    }

    private ResponseCookie issuedCookie() {
        return ResponseCookie.from("plog_media", "media-token")
                .httpOnly(true).secure(true).sameSite("None")
                .path("/api/chat-attachments").maxAge(Duration.ofDays(14)).build();
    }
}
