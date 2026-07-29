package com.plog.domain.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.chat.dto.response.ChatAttachmentDownload;
import com.plog.domain.chat.dto.response.ChatAttachmentMeta;
import com.plog.domain.chat.service.ChatAttachmentDownloadService;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaTokenProvider;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatAttachmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatAttachmentControllerTest {

    private static final String ETAG = "\"55\"";
    private static final String THUMB_ETAG = "\"55-t\"";

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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 첨부를_바이너리로_흘리고_캐시_헤더를_붙인다() throws Exception {
        authenticate(7L);
        byte[] body = "fake-png".getBytes(StandardCharsets.UTF_8);
        given(chatAttachmentDownloadService.resolve(3L, 7L)).willReturn(meta());
        given(chatAttachmentDownloadService.open(any(ChatAttachmentMeta.class)))
                .willReturn(new ChatAttachmentDownload(body.length, new ByteArrayInputStream(body)));

        mockMvc.perform(get("/api/chat-attachments/3"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().longValue("Content-Length", body.length))
                .andExpect(header().string("Cache-Control", "private, max-age=31536000, immutable"))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename*=UTF-8''photo.png"));
    }

    /**
     * 304 에서 S3 를 여는 순간 커넥션이 샌다 — Spring 이 본문 쓰기를 건너뛰어 아무도
     * 스트림을 닫지 않기 때문이다. open() 이 호출되지 않는 것이 이 설계의 핵심이다.
     */
    @Test
    void ifNoneMatch가_일치하면_304이고_S3를_열지_않는다() throws Exception {
        authenticate(7L);
        given(chatAttachmentDownloadService.resolve(3L, 7L)).willReturn(meta());

        mockMvc.perform(get("/api/chat-attachments/3").header(HttpHeaders.IF_NONE_MATCH, ETAG))
                .andExpect(status().isNotModified())
                .andExpect(header().string("Cache-Control", "private, max-age=31536000, immutable"));

        verify(chatAttachmentDownloadService, never()).open(any(ChatAttachmentMeta.class));
    }

    @Test
    void ifNoneMatch가_다르면_본문을_새로_내려준다() throws Exception {
        authenticate(7L);
        byte[] body = "fake-png".getBytes(StandardCharsets.UTF_8);
        given(chatAttachmentDownloadService.resolve(3L, 7L)).willReturn(meta());
        given(chatAttachmentDownloadService.open(any(ChatAttachmentMeta.class)))
                .willReturn(new ChatAttachmentDownload(body.length, new ByteArrayInputStream(body)));

        mockMvc.perform(get("/api/chat-attachments/3")
                        .header(HttpHeaders.IF_NONE_MATCH, "\"99\""))
                .andExpect(status().isOk());
    }

    @Test
    void 없는_첨부는_CHAT010() throws Exception {
        authenticate(7L);
        willThrow(new ApiException(ChatErrorCode.CHAT_ATTACHMENT_NOT_FOUND))
                .given(chatAttachmentDownloadService).resolve(999L, 7L);

        mockMvc.perform(get("/api/chat-attachments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT010"));
    }

    @Test
    void 방_멤버가_아니면_CHAT002() throws Exception {
        authenticate(7L);
        willThrow(new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS))
                .given(chatAttachmentDownloadService).resolve(3L, 7L);

        mockMvc.perform(get("/api/chat-attachments/3"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT002"));
    }

    @Test
    void 썸네일은_webp로_내려가고_immutable_캐시가_붙는다() throws Exception {
        authenticate(7L);
        byte[] body = "fake-webp".getBytes(StandardCharsets.UTF_8);
        given(chatAttachmentDownloadService.resolveThumbnail(3L, 7L)).willReturn(thumbMeta());
        given(chatAttachmentDownloadService.open(any(ChatAttachmentMeta.class)))
                .willReturn(new ChatAttachmentDownload(body.length, new ByteArrayInputStream(body)));

        mockMvc.perform(get("/api/chat-attachments/3/thumb"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andExpect(header().string("Cache-Control", "private, max-age=31536000, immutable"))
                .andExpect(header().string("ETag", THUMB_ETAG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                // 썸네일은 저장 대상이 아니라 filename 을 붙이지 않는다.
                .andExpect(header().string("Content-Disposition", "inline"));
    }

    /**
     * 304 경로에서 open() 이 호출되면 아무도 안 닫는 S3 스트림이 새기 시작한다.
     * 원본과 썸네일이 stream() 을 공유하므로 양쪽 다 이 회귀선을 갖는다.
     */
    @Test
    void 썸네일도_ETag가_일치하면_304이고_S3를_열지_않는다() throws Exception {
        authenticate(7L);
        given(chatAttachmentDownloadService.resolveThumbnail(3L, 7L)).willReturn(thumbMeta());

        mockMvc.perform(get("/api/chat-attachments/3/thumb")
                        .header(HttpHeaders.IF_NONE_MATCH, THUMB_ETAG))
                .andExpect(status().isNotModified());

        verify(chatAttachmentDownloadService, never()).open(any(ChatAttachmentMeta.class));
    }

    @Test
    void 아직_준비되지_않은_썸네일은_CHAT011() throws Exception {
        authenticate(7L);
        willThrow(new ApiException(ChatErrorCode.CHAT_THUMBNAIL_NOT_FOUND))
                .given(chatAttachmentDownloadService).resolveThumbnail(3L, 7L);

        mockMvc.perform(get("/api/chat-attachments/3/thumb"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT011"));
    }

    private ChatAttachmentMeta meta() {
        return new ChatAttachmentMeta(
                "chats/users/1/uuid/photo.png", "image/png", "photo.png", ETAG);
    }

    private ChatAttachmentMeta thumbMeta() {
        return new ChatAttachmentMeta(
                "thumbs/chats/users/1/uuid/photo.png.webp", "image/webp", "photo.png", THUMB_ETAG);
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null, java.util.List.of()));
    }
}
