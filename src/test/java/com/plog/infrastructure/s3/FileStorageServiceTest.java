package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.plog.global.api.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class FileStorageServiceTest {
    private final FileStorageService service = new FileStorageService(
            mock(S3Client.class), mock(S3Presigner.class));

    @BeforeEach
    void enableFileStorage() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
    }

    @Test
    void rejectsRequestWhenFileStorageIsDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);
        var request = new FileStorageDto.PresignedUploadRequest("report.pdf", "application/pdf", 1024L);

        assertThatThrownBy(() -> service.createUploadUrl(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(FileStorageErrorCode.FILE_STORAGE_DISABLED.getMessage());
    }

    @Test
    void rejectsUnsupportedExtensionBeforePresigning() {
        var request = new FileStorageDto.PresignedUploadRequest("design.fig", "application/octet-stream", 1024L);

        assertThatThrownBy(() -> service.createUploadUrl(request)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsOversizedFileBeforePresigning() {
        var request = new FileStorageDto.PresignedUploadRequest(
                "report.pdf", "application/pdf", FileStorageService.MAX_FILE_SIZE + 1);

        assertThatThrownBy(() -> service.createUploadUrl(request)).isInstanceOf(ApiException.class);
    }
}
