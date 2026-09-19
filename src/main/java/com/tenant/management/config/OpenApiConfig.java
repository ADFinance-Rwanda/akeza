package com.tenant.management.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI multiTenantOpenApi() {
        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Keycloak access token from realm taskmgr, client task-web. "
                        + "The API does not issue JWTs. Production profile also requires audience task-api.");

        return new OpenAPI()
                .info(new Info()
                        .title("Multi-Tenant Task Platform API")
                        .version("1.0.0")
                        .description("""
                                Keycloak-authenticated API for organizations, projects, and tasks.

                                Authentication: `Authorization: Bearer <access_token>`
                                Tenant scope: `X-Organization-Id` (required on projects, tasks, analytics, and other org-scoped work)
                                Task list: `GET /api/tasks` (paginated; filters status, priority, projectId, assigneeUserId, overdue, dueAfter, dueBefore, q)
                                Task create: `POST /api/tasks` with `projectId` in the body, or nested `POST /api/projects/{id}/tasks`. Both require `Idempotency-Key` (replay returns the stored 201 body; different body → 409)
                                Task get/update/delete: `GET|PUT|DELETE /api/tasks/{id}` aliases plus nested `/api/projects/{id}/tasks/{taskId}` (same TaskService)
                                Task update: JSON `version` from the last read (mismatch → 409)
                                Task writes: Redis rate limit → 429 with Retry-After; Redis down → 503 (fail-closed). Cache/dashboard fail-open.
                                Correlation: send `X-Request-Id` or the API generates one and echoes it

                                Typical errors: 400 validation, 401 missing/invalid JWT, 403 wrong org/role, \
                                404 hidden cross-tenant resource, 409 concurrency/idempotency, 429 rate limit, \
                                503 Redis unavailable on write rate-limit path.
                                """))
                .addServersItem(new Server().url("http://localhost:8080").description("Local API / Compose app"))
                .components(new Components().addSecuritySchemes("bearerAuth", bearerAuth))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OperationCustomizer commonHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name("X-Organization-Id")
                    .required(false)
                    .description("Membership-checked tenant id. Required for /api/projects, /api/analytics, and task routes.")
                    .schema(new StringSchema()));
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name("X-Request-Id")
                    .required(false)
                    .description("Optional correlation id. Echoed on the response; generated if omitted.")
                    .schema(new StringSchema()));
            return operation;
        };
    }
}
