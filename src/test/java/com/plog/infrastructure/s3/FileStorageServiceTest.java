package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedRequest;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(s3Client, s3Presigner);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "bucket", "plog-test");
    }

    @Test
    void createsADownloadUrlWithTheRequestedExpiration() throws Exception {
        given(presignedRequest.url()).willReturn(URI.create("https://storage.test/report.pdf").toURL());
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .willReturn(presignedRequest);

        FileStorageDto.PresignedDownloadResponse response = service.createDownloadUrl(
                "reports/1/report.pdf",
                Duration.ofSeconds(300)
        );

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(300));
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("plog-test");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("reports/1/report.pdf");
        assertThat(response.downloadUrl()).isEqualTo("https://storage.test/report.pdf");
        assertThat(response.expiresInSeconds()).isEqualTo(300);
    }
}
