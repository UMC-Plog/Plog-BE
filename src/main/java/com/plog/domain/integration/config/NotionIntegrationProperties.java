package com.plog.domain.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plog.integration.notion")
public record NotionIntegrationProperties(
        String clientId,
        String clientSecret,
        String callbackUrl
) {}
