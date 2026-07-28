package com.plog.domain.user.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.user.service.UserWithdrawalService;
import com.plog.global.config.CorsProperties;
import com.plog.global.config.SecurityConfig;
import com.plog.global.security.jwt.JwtAccessDeniedHandler;
import com.plog.global.security.jwt.JwtAuthenticationEntryPoint;
import com.plog.global.security.jwt.JwtAuthenticationFilter;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaCookieAuthenticationFilter;
import com.plog.global.security.jwt.MediaTokenProvider;
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
 * 탈퇴는 되돌릴 수 없으므로 인증 없는 호출이 컨트롤러에 도달하는 일이 절대 없어야 한다.
 * ProfileControllerSecurityTest와 같은 구성 — "/api/users/**"는 PUBLIC_AUTH_PATHS에 없으므로
 * Authorization 헤더 없는 DELETE는 SecurityConfig의 anyRequest().authenticated()에 걸려 401이어야 한다.
 * 서비스를 mock으로 두고 verifyNoInteractions로, 401이 서비스 호출 "전"에 발생하는지도 함께 검증한다.
 * 즉 이 경로가 실수로 공개 경로 목록에 추가되면 이 테스트가 깨진다.
 */
@WebMvcTest(UserController.class)
@EnableConfigurationProperties(CorsProperties.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        MediaCookieAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserWithdrawalService userWithdrawalService;

    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MediaTokenProvider mediaTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void withdrawRejectsRequestWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agreed":true}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        verifyNoInteractions(userWithdrawalService);
    }
}
