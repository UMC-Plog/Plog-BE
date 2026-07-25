package com.plog.domain.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "plog.integration.github")
public record GithubIntegrationProperties(
        String appId,
        String appSlug,
        String clientId,
        String clientSecret,
        String privateKeyBase64,
        String callbackUrl
) {}
