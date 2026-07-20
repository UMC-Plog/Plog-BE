package com.plog.global.api.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectSuccessCodeTest {

    @Test
    void usesSequentialProjectDomainCodes() {
        assertThat(ProjectSuccessCode.values())
                .extracting(ProjectSuccessCode::getCode)
                .containsExactly("PROJECT001", "PROJECT002", "PROJECT003");
    }
}
