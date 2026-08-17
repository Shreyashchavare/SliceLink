package com.slicelink.auth;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the global OpenAPI definition and the bearer-token security scheme
 * so that {@code @SecurityRequirement(name = "bearerAuth")} references resolve
 * in Swagger UI.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SliceLink API",
                version = "v1",
                description = "URL shortener — Phase 2: Authentication"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER)
public class OpenApiConfiguration {
}
