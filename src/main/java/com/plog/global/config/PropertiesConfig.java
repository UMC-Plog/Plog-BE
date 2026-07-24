package com.plog.global.config;

import com.plog.domain.integration.config.FigmaIntegrationProperties;
import com.plog.domain.integration.config.GithubIntegrationProperties;
import com.plog.domain.integration.config.GoogleIntegrationProperties;
import com.plog.domain.integration.config.IntegrationRedirectProperties;
import com.plog.domain.integration.config.NotionIntegrationProperties;
import com.plog.global.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** @ConfigurationProperties 바인딩 대상들을 한 곳에서 등록. */
@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        EmailVerificationProperties.class,
        CorsProperties.class,
        GithubIntegrationProperties.class,
        FigmaIntegrationProperties.class,
        NotionIntegrationProperties.class,
        GoogleIntegrationProperties.class,
        IntegrationRedirectProperties.class
})
public class PropertiesConfig {
}
