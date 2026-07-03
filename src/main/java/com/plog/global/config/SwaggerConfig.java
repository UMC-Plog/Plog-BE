package com.plog.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String JWT_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI plogOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Plog API")
                        .description("Plog 서버 API 문서")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(
                        JWT_SCHEME,
                        new SecurityScheme()
                                .name(JWT_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME));
    }
}
