package com.agrioptima.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI agriOptimaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AgriOptima API")
                        .version("0.2.0")
                        .description("""
                                AI-Powered Sustainable Fertilizer Optimization - hackathon prototype.

                                **Authentication:** call `POST /api/auth/register` or `POST /api/auth/login`, \
                                then click **Authorize** and paste the returned `accessToken`. \
                                All endpoints except register/login require a Bearer JWT.

                                Errors use RFC 7807 `application/problem+json`. Resources owned by other \
                                users return 404 (not 403) so their existence is not disclosed."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
