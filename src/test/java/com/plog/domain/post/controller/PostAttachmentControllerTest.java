package com.plog.domain.post.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.post.exception.PostErrorCode;
import com.plog.domain.post.service.PostAttachmentDownloadService;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.AttachmentDownloadResponse;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostAttachmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostAttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostAttachmentDownloadService postAttachmentDownloadService;

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
    void 발급된_URL과_파일명_만료를_내려준다() throws Exception {
        authenticate(7L);
        given(postAttachmentDownloadService.createDownloadUrl(1L, 3L, 7L))
                .willReturn(new AttachmentDownloadResponse(
                        3L, "요구사항_v2.docx", "https://storage.test/signed", 300L));

        mockMvc.perform(get("/api/projects/1/posts/attachments/3/download-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.attachmentId").value(3))
                .andExpect(jsonPath("$.result.fileName").value("요구사항_v2.docx"))
                .andExpect(jsonPath("$.result.downloadUrl").value("https://storage.test/signed"))
                .andExpect(jsonPath("$.result.expiresInSeconds").value(300));
    }

    @Test
    void 없는_첨부는_404() throws Exception {
        authenticate(7L);
        willThrow(new ApiException(PostErrorCode.POST_ATTACHMENT_NOT_FOUND))
                .given(postAttachmentDownloadService).createDownloadUrl(1L, 999L, 7L);

        mockMvc.perform(get("/api/projects/1/posts/attachments/999/download-url"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_ATTACHMENT_NOT_FOUND"));
    }

    @Test
    void LINK_첨부는_400() throws Exception {
        authenticate(7L);
        willThrow(new ApiException(PostErrorCode.INVALID_ATTACHMENT_TYPE))
                .given(postAttachmentDownloadService).createDownloadUrl(1L, 3L, 7L);

        mockMvc.perform(get("/api/projects/1/posts/attachments/3/download-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTACHMENT_TYPE"));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null, java.util.List.of()));
    }
}
