package com.plog.domain.user.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.user.service.PasswordResetService;
import com.plog.global.config.CorsProperties;
import com.plog.global.config.SecurityConfig;
import com.plog.global.security.jwt.JwtAccessDeniedHandler;
import com.plog.global.security.jwt.JwtAuthenticationEntryPoint;
import com.plog.global.security.jwt.JwtAuthenticationFilter;
import com.plog.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 비밀번호 재설정 3개 라우트가 실제로 매핑되고, PUBLIC_AUTH_PATHS("/api/auth/password/**")에 의해
 * Authorization 헤더 없이도 허용되는지 검증한다. 서비스는 mock으로 대체하므로 실제 비즈니스 로직/DB는 타지 않는다.
 */
@WebMvcTest(PasswordResetController.class)
@EnableConfigurationProperties(CorsProperties.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class PasswordResetControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void sendIsReachableWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/auth/password/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hello@plog.com\"}"))
                .andExpect(status().isOk());

        verify(passwordResetService).sendCode("hello@plog.com");
    }

    @Test
    void verifyIsReachableWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/auth/password/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hello@plog.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());

        verify(passwordResetService).verify("hello@plog.com", "123456");
    }

    @Test
    void resetIsReachableWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hello@plog.com\",\"newPassword\":\"plog1234\","
                                + "\"newPasswordConfirm\":\"plog1234\"}"))
                .andExpect(status().isOk());

        verify(passwordResetService).reset("hello@plog.com", "plog1234", "plog1234");
    }
}
