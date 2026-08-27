package com.enterprise.knowledge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI knowledgeBaseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise AI Knowledge Base API")
                        .version("0.1.0")
                        .description("""
                                Multi-tenant knowledge base with retrieval-augmented answers over \
                                uploaded documents.

                                Obtain a token from POST /api/auth/login and send it as \
                                `Authorization: Bearer <token>`. The tenant is taken from the \
                                token, never from a request parameter."""))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
