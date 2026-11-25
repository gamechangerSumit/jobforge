package com.jobforge.backend.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the generated backend contract. Documentation endpoints are opt-in by environment. */
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI jobForgeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("JobForge API")
                        .version("v1")
                        .description("Generated API contract for JobForge. Authentication uses bearer access tokens.")
                        .contact(new Contact().name("JobForge Engineering")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
