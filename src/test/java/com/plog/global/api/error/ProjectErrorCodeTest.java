package com.plog.global.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectErrorCodeTest {

    @Test
    void usesSequentialProjectDomainCodes() {
        assertThat(List.of(
                ProjectErrorCode.PROJECT_NOT_FOUND,
                ProjectErrorCode.PROJECT_MEMBER_REQUIRED,
                ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED,
                ProjectErrorCode.INVALID_PROJECT_NAME,
                ProjectErrorCode.INVALID_PROJECT_END_DAY,
                ProjectErrorCode.INVITE_TOKEN_CONFIGURATION_ERROR
        ))
                .extracting(ProjectErrorCode::getCode)
                .containsExactly(
                        "PROJECT001",
                        "PROJECT002",
                        "PROJECT003",
                        "PROJECT004",
                        "PROJECT005",
                        "PROJECT006"
                );
    }
}
