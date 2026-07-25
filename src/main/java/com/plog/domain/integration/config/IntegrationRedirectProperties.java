package com.plog.domain.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plog.integration.redirect")
public record IntegrationRedirectProperties(
        String successUrl,
        String failureUrl
) {}
