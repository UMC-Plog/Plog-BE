package com.plog.domain.post.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.post.dto.PostDto;
import com.plog.domain.post.service.PostService;
import com.plog.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerContractTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PostService postService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(7L, null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesAllElevenFeedEndpointsWithoutApiVersionPrefix() throws Exception {
        mockMvc.perform(post("/projects/1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"post\",\"isNotice\":false,\"attachments\":[]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/projects/1/posts"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/projects/1/posts/2"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/projects/1/posts/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"updated\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/projects/1/posts/2"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/projects/1/posts/2/notice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isNotice\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/projects/1/posts/2/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"comment\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/projects/1/posts/2/comments"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/projects/1/posts/2/comments/3"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/projects/1/posts/2/like"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/projects/1/posts/2/like"))
                .andExpect(status().isOk());

        verify(postService).getFeed(1L, 7L, null, 20);
        verify(postService).changeNotice(eq(1L), eq(2L), eq(7L), any(PostDto.NoticeRequest.class));
    }

    @Test
    void missingNoticeValueReturnsValidationError() throws Exception {
        mockMvc.perform(patch("/projects/1/posts/2/notice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
