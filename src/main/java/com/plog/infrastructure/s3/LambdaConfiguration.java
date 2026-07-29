package com.plog.infrastructure.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;

/**
 * 자격증명을 명시하지 않는다. EC2 에서는 기본 공급자 체인이 인스턴스 역할
 * (plog-ec2-role)을 잡는다 — S3Configuration 과 같은 방식이다.
 * <p>
 * 비동기 클라이언트를 쓰는 이유는 InvocationType.EVENT 라도 202 응답까지 50~200ms 가
 * 들기 때문이다. CompletableFuture 를 기다리지 않으면 SDK 이벤트 루프가 처리해
 * 새 스레드풀도 추가 설정도 필요 없다.
 */
@Configuration
public class LambdaConfiguration {

    @Bean
    LambdaAsyncClient lambdaAsyncClient(
            @Value("${plog.s3.region:ap-northeast-2}") String region
    ) {
        return LambdaAsyncClient.builder().region(Region.of(region)).build();
    }
}
