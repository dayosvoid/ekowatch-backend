package com.tesa.ekowatch.shared;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    public OpenAPI ekowatchOpenAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .name(BEARER_AUTH)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("HMAC-SHA256")
                .description("Pass the accessToken from POST /api/v1/auth/sessions (or /api/v1/auth/login) as: Authorization: Bearer <accessToken>");

        Schema<?> fieldErrorSchema = new ObjectSchema()
                .addProperty("field", new StringSchema().example("note"))
                .addProperty("message", new StringSchema().example("Note is required."))
                .required(List.of("field", "message"));

        StringSchema codeSchema = new StringSchema();
        codeSchema.setEnum(List.of(
                "VALIDATION_FAILED",
                "IDEMPOTENCY_KEY_REQUIRED",
                "UNAUTHENTICATED",
                "FORBIDDEN",
                "WARD_ASSIGNMENT_REQUIRED",
                "RESOURCE_NOT_FOUND",
                "SELF_CONFIRMATION_NOT_ALLOWED",
                "IDEMPOTENCY_KEY_REUSED",
                "PHOTO_TOO_LARGE",
                "UNSUPPORTED_PHOTO_TYPE",
                "INTERNAL_ERROR"
        ));

        Schema<?> problemDetailSchema = new ObjectSchema()
                .description("RFC 9457 / Problem Details error body returned with Content-Type: application/problem+json")
                .addProperty("type", new StringSchema().example("urn:ekowatch:problem:validation"))
                .addProperty("title", new StringSchema().example("Validation failed"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("code", codeSchema)
                .addProperty("detail", new StringSchema().example("Check the highlighted fields."))
                .addProperty("instance", new StringSchema().example("/api/v1/reports"))
                .addProperty("requestId", new StringSchema().example("d6c37192-5428-442d-a067-86c7336a842d"))
                .addProperty("fieldErrors", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/FieldError")))
                .required(List.of("type", "title", "status", "code", "detail", "requestId"));

        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, bearerScheme)
                .addSchemas("FieldError", fieldErrorSchema)
                .addSchemas("ProblemDetail", problemDetailSchema);

        return new OpenAPI()
                .info(new Info()
                        .title("EkoWatch API")
                        .version("1.0.0")
                        .description("""
                                Neighborhood Waste & Flood Alert System (Lagos) — Canonical REST API v1.
                                
                                ### Authentication
                                - **Public endpoints**: `POST /api/v1/auth/sessions` and `POST /api/v1/auth/login`
                                - **Protected endpoints**: All other `/api/v1/**` endpoints require `Authorization: Bearer <accessToken>`
                                
                                ### Error Format (`application/problem+json`)
                                Every error response (`400`, `401`, `403`, `404`, `409`, `413`, `415`, `500`) returns `Content-Type: application/problem+json` with a stable `code` field (`VALIDATION_FAILED`, `IDEMPOTENCY_KEY_REQUIRED`, `UNAUTHENTICATED`, `FORBIDDEN`, `WARD_ASSIGNMENT_REQUIRED`, `RESOURCE_NOT_FOUND`, `SELF_CONFIRMATION_NOT_ALLOWED`, `IDEMPOTENCY_KEY_REUSED`, `PHOTO_TOO_LARGE`, `UNSUPPORTED_PHOTO_TYPE`, `INTERNAL_ERROR`).
                                """))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    public OpenApiCustomizer problemDetailsOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                boolean isAuthLogin = path.startsWith("/api/v1/auth/");
                if (isAuthLogin) {
                    operation.setSecurity(List.of());
                } else {
                    operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
                }

                if (operation.getParameters() != null) {
                    operation.getParameters().forEach(param -> {
                        if ("Idempotency-Key".equalsIgnoreCase(param.getName())) {
                            param.setRequired(true);
                        }
                    });
                }

                ApiResponses responses = operation.getResponses();
                if (responses == null) {
                    responses = new ApiResponses();
                    operation.setResponses(responses);
                }

                if ((path.equals("/api/v1/reports") || path.equals("/api/v1/photos"))
                        && httpMethod == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                        && responses.containsKey("201")) {
                    responses.remove("200");
                }

                addProblemResponseIfAbsent(responses, "400", "Bad Request — VALIDATION_FAILED or IDEMPOTENCY_KEY_REQUIRED",
                        Map.of(
                                "VALIDATION_FAILED", new Example().value(Map.of(
                                        "type", "urn:ekowatch:problem:validation",
                                        "title", "Validation failed",
                                        "status", 400,
                                        "code", "VALIDATION_FAILED",
                                        "detail", "Check the highlighted fields.",
                                        "instance", path,
                                        "requestId", "d6c37192-5428-442d-a067-86c7336a842d",
                                        "fieldErrors", List.of(Map.of("field", "note", "message", "Note is required."))
                                )),
                                "IDEMPOTENCY_KEY_REQUIRED", new Example().value(Map.of(
                                        "type", "urn:ekowatch:problem:idempotency-key-required",
                                        "title", "Idempotency key required",
                                        "status", 400,
                                        "code", "IDEMPOTENCY_KEY_REQUIRED",
                                        "detail", "Header Idempotency-Key is required for report creation.",
                                        "instance", path,
                                        "requestId", "d6c37192-5428-442d-a067-86c7336a842d"
                                ))
                        ));

                addProblemResponseIfAbsent(responses, "401", "Unauthorized — UNAUTHENTICATED (missing, expired, or invalid credentials/token)",
                        Map.of(
                                "UNAUTHENTICATED", new Example().value(Map.of(
                                        "type", "urn:ekowatch:problem:unauthenticated",
                                        "title", "Authentication required",
                                        "status", 401,
                                        "code", "UNAUTHENTICATED",
                                        "detail", "Valid authentication credentials are required.",
                                        "instance", path,
                                        "requestId", "d6c37192-5428-442d-a067-86c7336a842d"
                                ))
                        ));

                if (!isAuthLogin) {
                    addProblemResponseIfAbsent(responses, "403", "Forbidden — FORBIDDEN or WARD_ASSIGNMENT_REQUIRED",
                            Map.of(
                                    "FORBIDDEN", new Example().value(Map.of(
                                            "type", "urn:ekowatch:problem:forbidden",
                                            "title", "Access denied",
                                            "status", 403,
                                            "code", "FORBIDDEN",
                                            "detail", "You are not authorized to perform this action.",
                                            "instance", path,
                                            "requestId", "d6c37192-5428-442d-a067-86c7336a842d"
                                    )),
                                    "WARD_ASSIGNMENT_REQUIRED", new Example().value(Map.of(
                                            "type", "urn:ekowatch:problem:ward-assignment-required",
                                            "title", "Ward assignment required",
                                            "status", 403,
                                            "code", "WARD_ASSIGNMENT_REQUIRED",
                                            "detail", "You are not assigned to this ward.",
                                            "instance", path,
                                            "requestId", "d6c37192-5428-442d-a067-86c7336a842d"
                                    ))
                            ));

                    addProblemResponseIfAbsent(responses, "404", "Not Found — RESOURCE_NOT_FOUND",
                            Map.of(
                                    "RESOURCE_NOT_FOUND", new Example().value(Map.of(
                                            "type", "urn:ekowatch:problem:not-found",
                                            "title", "Resource not found",
                                            "status", 404,
                                            "code", "RESOURCE_NOT_FOUND",
                                            "detail", "The requested resource was not found.",
                                            "instance", path,
                                            "requestId", "d6c37192-5428-442d-a067-86c7336a842d"
                                    ))
                            ));
                }

                if (path.contains("/reports")) {
                    addProblemResponseIfAbsent(responses, "409", "Conflict — SELF_CONFIRMATION_NOT_ALLOWED or IDEMPOTENCY_KEY_REUSED",
                            Map.of(
                                    "SELF_CONFIRMATION_NOT_ALLOWED", new Example().value(Map.of(
                                            "type", "urn:ekowatch:problem:self-confirmation-not-allowed",
                                            "title", "Self confirmation not allowed",
                                            "status", 409,
                                            "code", "SELF_CONFIRMATION_NOT_ALLOWED",
                                            "detail", "You cannot confirm your own report.",
                                            "instance", path,
                                            "requestId", "d6c37192-5428-442d-a067-86c7336a842d"
                                    )),
                                    "IDEMPOTENCY_KEY_REUSED", new Example().value(Map.of(
                                            "type", "urn:ekowatch:problem:idempotency-key-reused",
                                            "title", "Idempotency key reused",
                                            "status", 409,
                                            "code", "IDEMPOTENCY_KEY_REUSED",
                                            "detail", "Idempotency-Key was already used with a different request payload.",
                                            "instance", path,
                                            "requestId", "d6c37192-5428-442d-a067-86c7336a842d"
                                    ))
                            ));
                }

                if (path.equals("/api/v1/photos")) {
                    addProblemResponseIfAbsent(responses, "413", "Payload Too Large — PHOTO_TOO_LARGE (> 5 MiB)",
                            Map.of(
                                    "PHOTO_TOO_LARGE", new Example().value(Map.of(
                                            "type", "urn:ekowatch:problem:photo-too-large",
                                            "title", "Photo too large",
                                            "status", 413,
                                            "code", "PHOTO_TOO_LARGE",
                                            "detail", "Uploaded photo exceeds the 5 MiB size limit.",
                                            "instance", path,
                                            "requestId", "d6c37192-5428-442d-a067-86c7336a842d"
                                    ))
                            ));
                    addProblemResponseIfAbsent(responses, "415", "Unsupported Media Type — UNSUPPORTED_PHOTO_TYPE (only JPEG and PNG allowed)",
                            Map.of(
                                    "UNSUPPORTED_PHOTO_TYPE", new Example().value(Map.of(
                                            "type", "urn:ekowatch:problem:unsupported-photo-type",
                                            "title", "Unsupported photo type",
                                            "status", 415,
                                            "code", "UNSUPPORTED_PHOTO_TYPE",
                                            "detail", "Only JPEG and PNG photos are supported.",
                                            "instance", path,
                                            "requestId", "d6c37192-5428-442d-a067-86c7336a842d"
                                    ))
                            ));
                }
            }));
        };
    }

    private static void addProblemResponseIfAbsent(
            ApiResponses responses,
            String statusCode,
            String description,
            Map<String, Example> examples) {

        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"));
        examples.forEach(mediaType::addExamples);

        ApiResponse apiResponse = new ApiResponse()
                .description(description)
                .addHeaderObject("X-Request-Id", new Header().description("Request correlation ID").schema(new StringSchema()))
                .content(new Content().addMediaType(PROBLEM_JSON, mediaType));

        responses.put(statusCode, apiResponse);
    }
}
