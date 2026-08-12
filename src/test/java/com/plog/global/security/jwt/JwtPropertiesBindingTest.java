package com.plog.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * 실제 application.yaml 이 JwtProperties 로 바인딩되는지 본다.
 * <p>
 * 이 프로젝트에는 @SpringBootTest 가 없어서 전체 컨텍스트가 테스트에서 한 번도 뜨지 않는다.
 * 그래서 app.jwt.* 가 하나라도 빠지면 JwtProperties 의 검증이 기동을 막는데도
 * 배포하기 전까지 아무도 모른다. 기동을 좌우하는 값만이라도 여기서 잡는다.
 */
class JwtPropertiesBindingTest {

    private JwtProperties bindFromApplicationYaml() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));
        // secret 은 ${JWT_SECRET} placeholder 라 값을 넣어줘야 바인딩이 끝난다.
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test-secrets",
                Map.of("JWT_SECRET", "plog-test-secret-key-must-be-at-least-32-bytes")));

        return Binder.get(environment).bind("app.jwt", JwtProperties.class).get();
    }

    @Test
    @DisplayName("application.yaml 의 app.jwt 설정이 전부 바인딩된다")
    void bindsEveryJwtProperty() throws IOException {
        JwtProperties properties = bindFromApplicationYaml();

        assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofDays(14));
        assertThat(properties.mediaTokenTtl()).isEqualTo(Duration.ofDays(14));
    }

    /** 이 값이 비면 JwtProperties 가 기동을 막는다. 회전 유예가 없으면 재시도 한 번에 로그아웃된다. */
    @Test
    @DisplayName("회전 유예가 설정되어 있고 0보다 크다")
    void bindsPositiveRefreshTokenGrace() throws IOException {
        JwtProperties properties = bindFromApplicationYaml();

        assertThat(properties.refreshTokenGrace()).isNotNull();
        assertThat(properties.refreshTokenGrace()).isPositive();
    }
}
