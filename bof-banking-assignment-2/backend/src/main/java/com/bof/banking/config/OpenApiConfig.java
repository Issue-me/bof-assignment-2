package com.bof.banking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger documentation configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    /**
     * Handles custom open api.
     * @return the result of the operation.
     */
    public OpenAPI customOpenAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT token authentication");

        return new OpenAPI()
                .info(new Info()
                        .title("Bank of Fiji API")
                        .version("1.0.0")
                        .description("REST API for Bank of Fiji Online Banking System")
                        .contact(new Contact()
                                .name("BoF Development Team")
                                .email("dev@bof.com.fj"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth", securityScheme));
    }
}
