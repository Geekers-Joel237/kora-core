package com.geekersjoel237.koracore.shared.config;

import com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI koraOpenApi() {
        return new OpenAPI()
                .info(new Info().title("KORA Core API").version("v1"))
                .components(new Components()
                        .addSecuritySchemes(ApiSchemas.BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSchemas(ApiSchemas.PROBLEM, problem())
                        .addSchemas(ApiSchemas.SECURITY_ERROR, securityError()));
    }

    private static Schema<?> problem() {
        Schema<?> violation = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema())
                .required(List.of("field", "message"));
        return new ObjectSchema()
                .description("RFC 9457 problem detail")
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema())
                .addProperty("violations", new ArraySchema().items(violation)
                        .description("Present on a 400 raised by request validation"))
                .required(List.of("title", "status", "detail", "instance"));
    }

    private static Schema<?> securityError() {
        return new ObjectSchema()
                .description("Written by the security filter chain: missing, invalid or expired token, or a role that does not grant the route")
                .addProperty("status", new IntegerSchema())
                .addProperty("error", new StringSchema())
                .required(List.of("status", "error"));
    }
}
