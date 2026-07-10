package com.example.feat1.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "feat1 API",
            version = "v1",
            description = "Restaurant backend API documentation",
            contact = @Contact(name = "feat1")),
    security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
@SecurityScheme(
    name = OpenApiConfig.BEARER_AUTH,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER)
public class OpenApiConfig {
  public static final String BEARER_AUTH = "bearerAuth";
}
