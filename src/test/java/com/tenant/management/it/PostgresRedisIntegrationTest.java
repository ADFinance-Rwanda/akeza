package com.tenant.management.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.TestJwtSupport;
import com.tenant.management.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Testcontainers
@EnabledIf("dockerAvailable")
@Import(PostgresRedisIntegrationTest.JwtOnlyConfig.class)
class PostgresRedisIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("tenant_management")
            .withUsername("tenant")
            .withPassword("tenant");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    public static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void flywayTenantIdempotencyRateLimitAndCacheUseRealPostgresAndRedis() throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT Org\",\"slug\":\"it-org\"}"))
                .andExpect(status().isCreated());
        JsonNode orgs = objectMapper.readTree(mockMvc.perform(get("/api/organizations")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice()))
                .andReturn().getResponse().getContentAsString());
        long orgId = orgs.get(0).get("id").asLong();
        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"IT Project\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> create = () -> mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                            .header("Authorization", "Bearer " + TestJwtSupport.alice())
                            .header("X-Organization-Id", orgId)
                            .header("Idempotency-Key", "it-concurrent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Once\"}"))
                    .andReturn();
            Future<MvcResult> first = pool.submit(create);
            Future<MvcResult> second = pool.submit(create);
            List<Integer> statuses = new ArrayList<>(List.of(
                    first.get().getResponse().getStatus(),
                    second.get().getResponse().getStatus()));
            assertThat(statuses).contains(201);
            assertThat(statuses).allMatch(code -> code == 201 || code == 200);
            mockMvc.perform(get("/api/tasks")
                            .header("Authorization", "Bearer " + TestJwtSupport.alice())
                            .header("X-Organization-Id", orgId))
                    .andExpect(jsonPath("$.totalElements").value(1));
        } finally {
            pool.shutdownNow();
        }

        mockMvc.perform(get("/api/analytics/dashboard")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk());
        String cacheKey = AnalyticsService.cacheKey(orgId);
        assertThat(stringRedisTemplate.getExpire(cacheKey)).isGreaterThan(0);

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "it-rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"rate\"}"))
                .andExpect(status().isCreated());
        String rateKey = stringRedisTemplate.keys("ratelimit:*").stream().findFirst().orElseThrow();
        assertThat(stringRedisTemplate.getExpire(rateKey)).isGreaterThan(0);
    }

    @TestConfiguration
    static class JwtOnlyConfig {
        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return TestJwtSupport.decoder();
        }
    }
}
