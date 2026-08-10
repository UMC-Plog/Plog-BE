package com.plog.infrastructure.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 실제 application.yaml의 plog.embedding.* 값이 {@link EmbeddingProperties}에 바인딩되는지
 * 확인한다.
 * <p>
 * 예전에 embedding 블록이 실수로 llm 블록 안에 중첩돼서(plog.llm.embedding이 되어버림) 운영에
 * GEMINI_API_KEY를 넣어도 EmbeddingProperties가 항상 빈 값을 받아 Stub으로만 폴백하는 사고가
 * 있었다. 이 테스트는 임의의 문자열이 아니라 <b>실제 클래스패스의 application.yaml</b>을
 * 그대로 로드해서, 이런 들여쓰기 회귀를 다시 코드 리뷰가 아니라 테스트로 잡는다.
 */
class EmbeddingPropertiesYamlBindingTest {

    @Configuration
    @EnableConfigurationProperties(EmbeddingProperties.class)
    static class TestConfig {
    }

    @Test
    void application_yaml의_embedding_블록이_plog_embedding_경로로_바인딩된다() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources =
                loader.load("application.yaml", new ClassPathResource("application.yaml"));

        new ApplicationContextRunner()
                .withSystemProperties(
                        "GEMINI_API_KEY=test-key-from-yaml-binding-test",
                        "LOCAL_DB_URL=", "LOCAL_DB_USERNAME=", "LOCAL_DB_PASSWORD=",
                        "JWT_SECRET=", "MAIL_USERNAME=", "MAIL_PASSWORD=",
                        "INVITE_TOKEN_ENCRYPTION_KEY_BASE64=", "INVITE_BASE_URL="
                )
                .withInitializer(context -> sources.forEach(
                        source -> context.getEnvironment().getPropertySources().addLast(source)))
                .withUserConfiguration(TestConfig.class)
                .run(context -> {
                    EmbeddingProperties properties = context.getBean(EmbeddingProperties.class);

                    // plog.llm.embedding처럼 잘못 중첩됐다면 이 값들은 전부 null/기본값이라
                    // isGeminiUsable()이 false가 된다 — 여기가 회귀를 잡는 핵심 단언이다.
                    assertThat(properties.gemini().apiKey()).isEqualTo("test-key-from-yaml-binding-test");
                    assertThat(properties.gemini().model()).isEqualTo("gemini-embedding-001");
                    assertThat(properties.isGeminiUsable()).isTrue();
                });
    }
}