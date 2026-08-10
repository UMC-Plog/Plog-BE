package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPdfRendererTest {

    @Test
    void rendersPdfBytes() {
        ReportPdfRenderer renderer = new ReportPdfRenderer();
        ReflectionTestUtils.setField(renderer, "fontPath", "");

        byte[] bytes = renderer.render("<html><body><h1>Report</h1></body></html>");

        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
