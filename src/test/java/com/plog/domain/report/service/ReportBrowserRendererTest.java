package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

class ReportBrowserRendererTest {

    @Test
    void scalesReferenceReportWidthToA4PrintableWidth() {
        assertThat(ReportBrowserRenderer.A4_REPORT_SCALE)
                .isCloseTo(1.7487, within(0.0001));
    }

    @Test
    void createsA4PdfOptionsThatPreserveTheReportRatio() {
        Page.PdfOptions options = ReportBrowserRenderer.createPdfOptions();

        assertThat(options.format).isEqualTo("A4");
        assertThat(options.scale).isEqualTo(ReportBrowserRenderer.A4_REPORT_SCALE);
        assertThat(options.printBackground).isTrue();
        assertThat(options.preferCSSPageSize).isTrue();
    }
}
