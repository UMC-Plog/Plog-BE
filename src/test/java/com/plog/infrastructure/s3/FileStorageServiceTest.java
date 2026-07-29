package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.Tag;
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

    /** 키 생성과 presign 은 UploadedFileService 가 이어 붙인다. 테스트도 같은 순서로 호출한다. */
    private FileStorageDto.PresignedUploadResponse createUpload(
            Long userId, FileStorageDto.PresignedUploadRequest request) {
        return service.createUploadUrl(userId, request,
                service.buildKey(request.usage(), userId, request.fileName()));
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
                createUpload(1L, upload(fileName, contentType, 1024L));

        assertThat(response.fileKey()).endsWith("/" + fileName);
    }

    @Test
    void rejectsAnImageOverTenMegabytes() {
        assertThatThrownBy(() -> createUpload(
                1L, upload("photo.png", "image/png", FileStorageService.MAX_IMAGE_SIZE + 1)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.FILE_SIZE_EXCEEDED));
    }

    @Test
    void acceptsAnImageExactlyAtTheLimit() throws Exception {
        stubPresignPut();

        FileStorageDto.PresignedUploadResponse response = createUpload(
                1L, upload("photo.png", "image/png", FileStorageService.MAX_IMAGE_SIZE));

        assertThat(response.fileKey()).endsWith("/photo.png");
    }

    @Test
    void keepsTheLargerLimitForDocuments() throws Exception {
        stubPresignPut();

        FileStorageDto.PresignedUploadResponse response = createUpload(
                1L, upload("deck.pptx",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        30L * 1024 * 1024));

        assertThat(response.fileKey()).endsWith("/deck.pptx");
    }

    @Test
    void rejectsSvgBecauseItCanCarryScripts() {
        assertThatThrownBy(() -> createUpload(
                1L, upload("logo.svg", "image/svg+xml", 1024L)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE));
    }

    @Test
    void rejectsAContentTypeThatDoesNotMatchTheExtension() {
        assertThatThrownBy(() -> createUpload(
                1L, upload("photo.png", "image/jpeg", 1024L)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE));
    }

    @Test
    void reportsAnUnsupportedExtensionEvenWhenTheFileIsAlsoTooLarge() {
        assertThatThrownBy(() -> createUpload(
                1L, upload("malware.exe", "application/octet-stream", 999L * 1024 * 1024)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE));
    }

    @ParameterizedTest
    @EnumSource(AttachmentUsage.class)
    void putsTheUsageIntoTheObjectKey(AttachmentUsage usage) throws Exception {
        stubPresignPut();

        FileStorageDto.PresignedUploadResponse response = createUpload(
                7L, new FileStorageDto.PresignedUploadRequest(
                        "report.pdf", "application/pdf", 1024L, usage));

        assertThat(response.fileKey())
                .startsWith(usage.keySegment() + "/users/7/")
                .endsWith("/report.pdf");
    }

    @Test
    void 파일명을_넘기면_내려받기_disposition_을_붙인다() throws Exception {
        given(presignedRequest.url()).willReturn(URI.create("https://storage.test/f").toURL());
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .willReturn(presignedRequest);

        service.createDownloadUrl(
                "temporary/post/users/1/a/note.pdf", "note.pdf", Duration.ofSeconds(300));

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().getObjectRequest().responseContentDisposition())
                .isEqualTo("attachment; filename*=UTF-8''note.pdf");
    }

    @Test
    void 파일명을_안_넘기면_disposition_없이_인라인으로_둔다() throws Exception {
        given(presignedRequest.url()).willReturn(URI.create("https://storage.test/f").toURL());
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .willReturn(presignedRequest);

        service.createDownloadUrl(
                "temporary/chat/users/1/a/photo.png", Duration.ofSeconds(300));

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().getObjectRequest().responseContentDisposition()).isNull();
    }

    @Test
    void applyState는_state와_ownerId를_함께_쓴다() {
        boolean tagged = service.applyState("chats/users/7/uuid/a.png",
                UploadedFileStatus.CONFIRMED, 7L);

        ArgumentCaptor<PutObjectTaggingRequest> captor =
                ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        verify(s3Client).putObjectTagging(captor.capture());

        assertThat(tagged).isTrue();
        assertThat(captor.getValue().tagging().tagSet())
                .extracting(Tag::key, Tag::value)
                .containsExactlyInAnyOrder(
                        tuple("state", "confirmed"),
                        tuple("ownerId", "7"));
    }

    @Test
    void 객체가_없으면_applyState는_false를_반환한다() {
        given(s3Client.putObjectTagging(any(PutObjectTaggingRequest.class)))
                .willThrow(NoSuchKeyException.builder().message("missing").build());

        boolean tagged = service.applyState("chats/users/7/uuid/gone.png",
                UploadedFileStatus.ORPHANED, 7L);

        assertThat(tagged).isFalse();
    }

    @Test
    void openStream은_객체가_없으면_비어있는_Optional을_준다() {
        given(s3Client.getObject(any(GetObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().message("missing").build());

        assertThat(service.openStream("chats/users/1/uuid/a.png")).isEmpty();
    }

    @Test
    void openStream은_S3_스트림을_그대로_전달한다() {
        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> stream = mock(ResponseInputStream.class);
        given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(stream);

        assertThat(service.openStream("chats/users/1/uuid/a.png")).contains(stream);
    }

    @Test
    void fig_파일은_octet_stream_으로_업로드할_수_있다() throws Exception {
        stubPresignPut();

        FileStorageDto.PresignedUploadResponse response = createUpload(
                7L, upload("디자인_시안_v3.fig", "application/octet-stream", 3L * 1024 * 1024));

        assertThat(response.fileKey()).endsWith(".fig");
    }

    @Test
    void fig_파일에_다른_MIME_을_보내면_거부한다() {
        assertThatThrownBy(() -> createUpload(
                7L, upload("디자인_시안_v3.fig", "image/png", 1024L)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", FileStorageErrorCode.UNSUPPORTED_ATTACHMENT_TYPE);
    }

    @Test
    void fig_파일은_이미지가_아니라_50MB_까지_허용한다() throws Exception {
        stubPresignPut();

        FileStorageDto.PresignedUploadResponse response = createUpload(
                7L, upload("큰파일.fig", "application/octet-stream", 40L * 1024 * 1024));

        assertThat(response.fileKey()).endsWith(".fig");
    }
}
