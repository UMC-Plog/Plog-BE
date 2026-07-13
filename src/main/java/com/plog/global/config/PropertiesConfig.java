package com.plog.global.config;

import com.plog.global.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** @ConfigurationProperties 바인딩 대상들을 한 곳에서 등록. */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, EmailVerificationProperties.class})
public class PropertiesConfig {
}
