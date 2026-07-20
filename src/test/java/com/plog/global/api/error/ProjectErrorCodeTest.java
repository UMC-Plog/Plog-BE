package com.plog.global.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectErrorCodeTest {

    @Test
    void usesSequentialProjectDomainCodes() {
        assertThat(ProjectErrorCode.values())
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
