package com.plog.infrastructure.s3;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.global.security.jwt.JwtProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(FileStorageController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileStorageControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FileStorageService fileStorageService;
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
    void createsSinglePresignedUrlWithoutApiVersionPrefix() throws Exception {
        given(fileStorageService.createUploadUrl(eq(7L), any(FileStorageDto.PresignedUploadRequest.class)))
                .willReturn(new FileStorageDto.PresignedUploadResponse(
                        "https://storage.example/upload",
                        "temporary/users/7/id/document.pdf",
                        Map.of("x-amz-tagging", List.of("state=temporary&ownerId=7")),
                        Instant.parse("2026-07-21T01:10:00Z")));

        mockMvc.perform(post("/files/presigned-upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"document.pdf","contentType":"application/pdf","fileSize":1024}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.uploadUrl").value("https://storage.example/upload"))
                .andExpect(jsonPath("$.result.signedHeaders.x-amz-tagging[0]")
                        .value("state=temporary&ownerId=7"));

        verify(fileStorageService).createUploadUrl(eq(7L), any(FileStorageDto.PresignedUploadRequest.class));
    }

    @Test
    void invalidFileSizeUsesValidationContract() throws Exception {
        mockMvc.perform(post("/files/presigned-upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"document.pdf","contentType":"application/pdf","fileSize":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
