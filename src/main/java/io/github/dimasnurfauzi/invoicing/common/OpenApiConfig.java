package io.github.dimasnurfauzi.invoicing.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for Swagger UI. Every endpoint requires the bearer token by default; public ones
 * (registration, login) opt out with an empty {@code @SecurityRequirements}.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info =
        @Info(
            title = "Invoicing SaaS API",
            version = "v1",
            description =
                "Register a tenant, log in, then click Authorize and paste the accessToken."),
    security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(
    name = OpenApiConfig.BEARER,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {

  public static final String BEARER = "bearerAuth";
}
