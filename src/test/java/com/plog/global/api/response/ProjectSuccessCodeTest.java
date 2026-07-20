package com.plog.global.api.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectSuccessCodeTest {

    @Test
    void usesSequentialProjectDomainCodes() {
        assertThat(List.of(
                ProjectSuccessCode.EXTERNAL_LINKS_RETRIEVED,
                ProjectSuccessCode.PROJECT_SETTINGS_RETRIEVED,
                ProjectSuccessCode.PROJECT_SETTINGS_UPDATED
        ))
                .extracting(ProjectSuccessCode::getCode)
                .containsExactly("PROJECT001", "PROJECT002", "PROJECT003");
    }
}
