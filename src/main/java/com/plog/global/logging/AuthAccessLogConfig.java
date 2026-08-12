package com.plog.global.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class AuthAccessLogConfig {

    /**
     * @Component 로 올리지 않는 이유: 그러면 Boot 가 전 경로에 자동 등록한다. 인증 경로에만 걸어야 한다.
     * 순서를 최상위로 두어 시큐리티 체인이 끊은 응답(401 등)의 상태 코드까지 잡는다.
     */
    @Bean
    public FilterRegistrationBean<AuthAccessLogFilter> authAccessLogFilter() {
        FilterRegistrationBean<AuthAccessLogFilter> registration =
                new FilterRegistrationBean<>(new AuthAccessLogFilter());
        registration.addUrlPatterns("/api/auth/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
