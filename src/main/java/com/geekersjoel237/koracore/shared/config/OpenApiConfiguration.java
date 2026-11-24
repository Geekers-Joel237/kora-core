package com.geekersjoel237.koracore.shared.config;

import com.geekersjoel237.koracore.shared.adapters.in.rest.ApiSchemas;
import com.geekersjoel237.koracore.shared.adapters.in.rest.CorrelationId;
import com.geekersjoel237.koracore.shared.adapters.in.rest.exception.ProblemDetails;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
                        .addSchemas(ApiSchemas.PROBLEM, problem()));
    }

    @Bean
    OpenApiCustomizer correlationIdOnCommandResponses() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            openApi.getPaths().values().stream()
                    .map(path -> path.getPost())
                    .filter(post -> post != null && post.getResponses() != null)
                    .flatMap(post -> post.getResponses().values().stream())
                    .forEach(response -> response.addHeaderObject(CorrelationId.HEADER, new Header()
                            .description("The correlation id of the command: the one the request sent, or the one the server generated")
                            .schema(new StringSchema())));
        };
    }

    private static Schema<?> problem() {
        Schema<?> violation = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema())
                .required(List.of("field", "message"));
        return new ObjectSchema()
                .description("RFC 9457 problem detail")
                .addProperty("type", new StringSchema().format("uri")
                        .description("The problem's entry in the catalog, " + ProblemDetails.CATALOG))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema())
                .addProperty(ProblemDetails.CODE, new StringSchema()
                        .description("Stable machine-readable code of the problem, such as INSUFFICIENT_FUNDS; the catalog lists every one"))
                .addProperty(CorrelationId.PROPERTY, new StringSchema()
                        .description("The correlation id of the request, present when it carried or generated one"))
                .addProperty("violations", new ArraySchema().items(violation)
                        .description("Present on a 400 raised by request validation"))
                .required(List.of("type", "title", "status", "detail", "instance", ProblemDetails.CODE));
    }
}
