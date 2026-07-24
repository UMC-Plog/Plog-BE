package com.plog.domain.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plog.integration.figma")
public record FigmaIntegrationProperties(
        String clientId,
        String clientSecret,
        String callbackUrl
) {}
