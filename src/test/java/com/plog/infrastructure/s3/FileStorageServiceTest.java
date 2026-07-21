package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.plog.global.api.exception.ApiException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedRequest;

    @Mock
    private PresignedPutObjectRequest presignedPut;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(s3Client, s3Presigner);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "bucket", "plog-test");
    }

    private void stubPresignPut() throws Exception {
        given(presignedPut.url()).willReturn(URI.create("https://storage.test/upload").toURL());
        given(presignedPut.signedHeaders()).willReturn(Map.of());
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presignedPut);
    }

    private FileStorageDto.PresignedUploadRequest upload(String fileName, String contentType, long size) {
        return new FileStorageDto.PresignedUploadRequest(
                fileName, contentType, size, AttachmentUsage.POST);
    }

    @Test
    void createsADownloadUrlWithTheRequestedExpiration() throws Exception {
        given(presignedRequest.url()).willReturn(URI.create("https://storage.test/report.pdf").toURL());
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .willReturn(presignedRequest);

        FileStorageDto.PresignedDownloadResponse response = service.createDownloadUrl(
                "reports/1/report.pdf",
                "Plog-report.pdf",
                Duration.ofSeconds(300)
        );

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(300));
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("plog-test");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("reports/1/report.pdf");
        assertThat(captor.getValue().getObjectRequest().responseContentDisposition())
                .isEqualTo("attachment; filename*=UTF-8''Plog-report.pdf");
        assertThat(response.downloadUrl()).isEqualTo("https://storage.test/report.pdf");
        assertThat(response.expiresInSeconds()).isEqualTo(300);
    }

    @ParameterizedTest
    @CsvSource({
            "photo.jpg,image/jpeg",
            "photo.jpeg,image/jpeg",
            "photo.png,image/png",
            "photo.webp,image/webp",
            "photo.gif,image/gif"
    })
    void acceptsEveryAllowedImageFormat(String fileName, String contentType) throws Exception {
        stubPresignPut();

        FileStorageDto.PresignedUploadResponse response =
                service.createUploadUrl(1L, upload(fileName, contentType, 1024L));

        assertThat(response.fileKey()).endsWith("/" + fileName);
    }

    @Test
    void rejectsAnImageOverTenMegabytes() {
        assertThatThrownBy(() -> service.createUploadUrl(
                1L, upload("photo.png", "image/png", FileStorageService.MAX_IMAGE_SIZE + 1)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.FILE_SIZE_EXCEEDED));
    }

    @Test
    void acceptsAnImageExactlyAtTheLimit() throws Exception {
        stubPresignPut();

        FileStorageDto.PresignedUploadResponse response = service.createUploadUrl(
                1L, upload("photo.png", "image/png", FileStorageService.MAX_IMAGE_SIZE));

        assertThat(response.fileKey()).endsWith("/photo.png");
    }

    @Test
    void keepsTheLargerLimitForDocuments() throws Exception {
        stubPresignPut();

        FileStorageDto.PresignedUploadResponse response = service.createUploadUrl(
                1L, upload("deck.pptx",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        30L * 1024 * 1024));

        assertThat(response.fileKey()).endsWith("/deck.pptx");
    }

    @Test
    void rejectsSvgBecauseItCanCarryScripts() {
        assertThatThrownBy(() -> service.createUploadUrl(
                1L, upload("logo.svg", "image/svg+xml", 1024L)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE));
    }

    @Test
    void rejectsAContentTypeThatDoesNotMatchTheExtension() {
        assertThatThrownBy(() -> service.createUploadUrl(
                1L, upload("photo.png", "image/jpeg", 1024L)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE));
    }

    @Test
    void reportsAnUnsupportedExtensionEvenWhenTheFileIsAlsoTooLarge() {
        assertThatThrownBy(() -> service.createUploadUrl(
                1L, upload("malware.exe", "application/octet-stream", 999L * 1024 * 1024)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE));
    }

    @ParameterizedTest
    @EnumSource(AttachmentUsage.class)
    void putsTheUsageIntoTheObjectKey(AttachmentUsage usage) throws Exception {
        stubPresignPut();

        FileStorageDto.PresignedUploadResponse response = service.createUploadUrl(
                7L, new FileStorageDto.PresignedUploadRequest(
                        "report.pdf", "application/pdf", 1024L, usage));

        assertThat(response.fileKey())
                .startsWith("temporary/" + usage.keySegment() + "/users/7/")
                .endsWith("/report.pdf");
    }

    @Test
    void attachesADownloadDispositionForPostAttachments() throws Exception {
        given(presignedRequest.url()).willReturn(URI.create("https://storage.test/f").toURL());
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .willReturn(presignedRequest);

        service.createDownloadUrl(
                AttachmentUsage.POST, "temporary/post/users/1/a/note.pdf", "note.pdf");

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().getObjectRequest().responseContentDisposition())
                .isEqualTo("attachment; filename*=UTF-8''note.pdf");
    }

    @Test
    void leavesChatAttachmentsInlineSoImagesRender() throws Exception {
        given(presignedRequest.url()).willReturn(URI.create("https://storage.test/f").toURL());
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .willReturn(presignedRequest);

        service.createDownloadUrl(
                AttachmentUsage.CHAT, "temporary/chat/users/1/a/photo.png", "photo.png");

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().getObjectRequest().responseContentDisposition()).isNull();
    }

    @Test
    void rejectsAKeyThatBelongsToAnotherUsage() {
        String chatKey = "temporary/chat/users/7/abc/report.pdf";

        assertThatThrownBy(() -> service.verifyUploadedFile(
                AttachmentUsage.POST, 7L, chatKey, "report.pdf", 1024L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.INVALID_FILE_KEY));
    }
}
