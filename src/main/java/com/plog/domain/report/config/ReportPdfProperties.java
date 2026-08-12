package com.plog.domain.report.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plog.report.pdf")
public record ReportPdfProperties(
        String renderBaseUrl,
        Duration timeout
) {
    public ReportPdfProperties {
        if (renderBaseUrl == null || renderBaseUrl.isBlank()) {
            throw new IllegalArgumentException("plog.report.pdf.render-base-url is required");
        }
        timeout = timeout == null ? Duration.ofSeconds(45) : timeout;
    }
}
