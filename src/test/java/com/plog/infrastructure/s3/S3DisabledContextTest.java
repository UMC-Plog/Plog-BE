package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plog.global.api.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class S3DisabledContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(S3Configuration.class, FileStorageService.class)
            .withPropertyValues("plog.s3.enabled=false", "plog.s3.bucket=");

    @Test
    void startsWithoutS3CredentialsAndFailsOnlyWhenStorageIsUsed() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            FileStorageService storage = context.getBean(FileStorageService.class);
            assertThatThrownBy(() -> storage.createDownloadUrl("reports/1/report.pdf"))
                    .isInstanceOfSatisfying(ApiException.class, exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(FileStorageErrorCode.FILE_STORAGE_DISABLED));
        });
    }
}
