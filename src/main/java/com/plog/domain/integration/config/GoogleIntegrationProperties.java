package com.plog.domain.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plog.integration.google")
public record GoogleIntegrationProperties(
        String clientId,
        String clientSecret,
        String callbackUrl
) {}
