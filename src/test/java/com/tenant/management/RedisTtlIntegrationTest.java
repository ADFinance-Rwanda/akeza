package com.tenant.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.service.AnalyticsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
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
import redis.embedded.RedisServer;

import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("redis-it")
@Import(RedisTtlIntegrationTest.JwtOnlyConfig.class)
class RedisTtlIntegrationTest {

    private static final RedisServer REDIS_SERVER;
    private static final int REDIS_PORT;

    static {
        try (ServerSocket socket = new ServerSocket(0)) {
            REDIS_PORT = socket.getLocalPort();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        try {
            REDIS_SERVER = RedisServer.newRedisServer().port(REDIS_PORT).build();
            REDIS_SERVER.start();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @AfterAll
    static void stopRedis() throws Exception {
        REDIS_SERVER.stop();
    }

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dashboardCacheKeyHasPositiveTtlAndIsInvalidatedOnCreate() throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TTL Org\",\"slug\":\"ttl-org\"}"))
                .andExpect(status().isCreated());
        JsonNode orgs = objectMapper.readTree(mockMvc.perform(get("/api/organizations")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice()))
                .andReturn()
                .getResponse()
                .getContentAsString());
        long orgId = orgs.get(0).get("id").asLong();

        mockMvc.perform(get("/api/analytics/dashboard")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk());

        String key = AnalyticsService.cacheKey(orgId);
        assertThat(stringRedisTemplate.hasKey(key)).isTrue();
        Long ttl = stringRedisTemplate.getExpire(key);
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(45);

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TTL Project\"}"))
                .andExpect(status().isCreated());
        JsonNode projects = objectMapper.readTree(mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId))
                .andReturn()
                .getResponse()
                .getContentAsString());
        long projectId = projects.get(0).get("id").asLong();
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "ttl-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"ttl\"}"))
                .andExpect(status().isCreated());

        assertThat(stringRedisTemplate.hasKey(key)).isFalse();

        mockMvc.perform(get("/api/analytics/dashboard")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk());
        assertThat(stringRedisTemplate.hasKey(key)).isTrue();
        assertThat(stringRedisTemplate.getExpire(key)).isGreaterThan(0);
    }

    @Test
    void rateLimitLuaScriptSetsTtlAndReturns429() throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RL Org\",\"slug\":\"rl-ttl-org\"}"))
                .andExpect(status().isCreated());
        JsonNode orgs = objectMapper.readTree(mockMvc.perform(get("/api/organizations")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice()))
                .andReturn()
                .getResponse()
                .getContentAsString());
        long orgId = -1;
        for (JsonNode node : orgs) {
            if ("rl-ttl-org".equals(node.get("slug").asText())) {
                orgId = node.get("id").asLong();
            }
        }
        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RL Project\"}"))
                .andExpect(status().isCreated());
        JsonNode projects = objectMapper.readTree(mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId))
                .andReturn()
                .getResponse()
                .getContentAsString());
        long projectId = projects.get(0).get("id").asLong();
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + TestJwtSupport.alice())
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "rl-lua-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"one\"}"))
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
