package com.plog.domain.report.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plog.report.pdf")
public record ReportPdfProperties(
        String renderBaseUrl,
        String chromiumExecutable,
        Duration timeout
) {
    public ReportPdfProperties {
        if (renderBaseUrl == null || renderBaseUrl.isBlank()) {
            throw new IllegalArgumentException("plog.report.pdf.render-base-url is required");
        }
        if (chromiumExecutable == null || chromiumExecutable.isBlank()) {
            throw new IllegalArgumentException("plog.report.pdf.chromium-executable is required");
        }
        timeout = timeout == null ? Duration.ofSeconds(45) : timeout;
    }
}
