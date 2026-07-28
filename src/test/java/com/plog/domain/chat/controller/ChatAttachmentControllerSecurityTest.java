package com.plog.domain.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.chat.dto.response.ChatAttachmentDownload;
import com.plog.domain.chat.dto.response.ChatAttachmentMeta;
import com.plog.domain.chat.service.ChatAttachmentDownloadService;
import com.plog.global.config.SecurityConfig;
import com.plog.global.security.jwt.JwtAccessDeniedHandler;
import com.plog.global.security.jwt.JwtAuthenticationEntryPoint;
import com.plog.global.security.jwt.JwtAuthenticationFilter;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaCookieAuthenticationFilter;
import com.plog.global.security.jwt.MediaCookieFactory;
import com.plog.global.security.jwt.MediaTokenProvider;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 프록시의 인증 경계를 <b>필터 체인이 살아 있는 상태로</b> 검증한다.
 * <p>
 * 이 설계 전체가 "미디어 필터가 이 경로에서 실제로 돈다"에 걸려 있다. 필터 단위 테스트는
 * 필터를 직접 new 해서 볼 뿐, SecurityConfig 의 등록·체인 순서·anyRequest().authenticated()
 * 와의 상호작용·EntryPoint 의 401 포맷은 여기서만 검증된다.
 */
@WebMvcTest(ChatAttachmentController.class)
@EnableConfigurationProperties(com.plog.global.config.CorsProperties.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        MediaCookieAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class ChatAttachmentControllerSecurityTest {

    private static final String ETAG = "\"55\"";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatAttachmentDownloadService chatAttachmentDownloadService;

    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MediaTokenProvider mediaTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void 쿠키도_헤더도_없으면_401이고_서비스를_부르지_않는다() throws Exception {
        mockMvc.perform(get("/api/chat-attachments/3"))
                .andExpect(status().isUnauthorized())
                // 이 경로는 JwtAuthenticationEntryPoint 의 isAssignedApi 목록 밖이라
                // 일반 ErrorCode.UNAUTHORIZED(COMMON401) 포맷으로 나간다.
                .andExpect(jsonPath("$.code").value("COMMON401"));

        verifyNoInteractions(chatAttachmentDownloadService);
    }

    @Test
    void 미디어_쿠키만_있어도_통과한다() throws Exception {
        given(mediaTokenProvider.parseUserId("media-token")).willReturn(7L);
        stubDownload();

        mockMvc.perform(get("/api/chat-attachments/3")
                        .cookie(new Cookie(MediaCookieFactory.COOKIE_NAME, "media-token")))
                .andExpect(status().isOk());
    }

    /**
     * 차단은 한 방향이다 — media 토큰은 다른 API 에 못 통하지만, 유효한 Authorization
     * 헤더는 이 엔드포인트에도 통해야 한다. Swagger Authorize 와 통합 테스트가 여기 걸린다.
     */
    @Test
    void 쿠키_없이_유효한_Authorization_헤더만_있어도_통과한다() throws Exception {
        given(jwtProvider.parseUserId("access-token")).willReturn(7L);
        stubDownload();

        mockMvc.perform(get("/api/chat-attachments/3")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk());
    }

    @Test
    void 망가진_미디어_쿠키는_401이다() throws Exception {
        given(mediaTokenProvider.parseUserId("broken"))
                .willThrow(new MalformedJwtException("bad"));

        mockMvc.perform(get("/api/chat-attachments/3")
                        .cookie(new Cookie(MediaCookieFactory.COOKIE_NAME, "broken")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH011"));

        verifyNoInteractions(chatAttachmentDownloadService);
    }

    private void stubDownload() {
        byte[] body = "fake-png".getBytes(StandardCharsets.UTF_8);
        given(chatAttachmentDownloadService.resolve(3L, 7L)).willReturn(new ChatAttachmentMeta(
                "chats/users/1/uuid/photo.png", "image/png", "photo.png", ETAG));
        given(chatAttachmentDownloadService.open(any(ChatAttachmentMeta.class)))
                .willReturn(new ChatAttachmentDownload(body.length, new ByteArrayInputStream(body)));
    }
}
