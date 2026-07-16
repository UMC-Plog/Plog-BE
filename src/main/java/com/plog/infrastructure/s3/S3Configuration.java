package com.plog.infrastructure.s3;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableAsync
public class S3Configuration {
    @Bean
    S3Client s3Client(
            @Value("${plog.s3.region:ap-northeast-2}") String region,
            @Value("${plog.s3.endpoint:}") String endpoint
    ) {
        var builder = S3Client.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(
            @Value("${plog.s3.region:ap-northeast-2}") String region,
            @Value("${plog.s3.endpoint:}") String endpoint
    ) {
        var builder = S3Presigner.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
