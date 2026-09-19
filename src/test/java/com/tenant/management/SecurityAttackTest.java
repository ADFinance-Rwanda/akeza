package com.tenant.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.entity.User;
import com.tenant.management.repository.UserRepository;
import com.tenant.management.service.AnalyticsService;
import com.tenant.management.service.JobProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class SecurityAttackTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void unauthenticatedMethodsAre401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/projects/1/tasks/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/projects/1/tasks/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analytics/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokensAre401AndDoNotLeakInternals() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized"))
                .andExpect(content().string(not(containsString("stack"))))
                .andExpect(content().string(not(containsString("Exception"))));
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhIn0.sig"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + TestJwtSupport.expired()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + TestJwtSupport.wrongIssuer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + TestJwtSupport.wrongSignature()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + TestJwtSupport.alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("alice@example.com"));
    }

    @Test
    void tenantIsolationAndPrivilegeEscalation() throws Exception {
        Long orgA = createOrg(TestJwtSupport.alice(), "Sec Org A", "sec-org-a");
        Long orgB = createOrg(TestJwtSupport.bob(), "Sec Org B", "sec-org-b");
        long projectA = createProject(TestJwtSupport.alice(), orgA, "PA");
        long projectB = createProject(TestJwtSupport.bob(), orgB, "PB");
        long taskA = createTask(TestJwtSupport.alice(), orgA, projectA, "Task A1", "iso-a1");
        long taskB = createTask(TestJwtSupport.bob(), orgB, projectB, "Task B1", "iso-b1");

        mockMvc.perform(get("/api/projects").header("Authorization", bearer(TestJwtSupport.alice())).header("X-Organization-Id", orgB))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/" + projectB).header("Authorization", bearer(TestJwtSupport.alice())).header("X-Organization-Id", orgA))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/projects/" + projectB + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "cross-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/" + projectB + "/tasks/" + taskB)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/projects/" + projectB + "/tasks/" + taskB)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hack\",\"version\":0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/projects/" + projectB + "/tasks/" + taskB)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/projects/" + projectA + "/tasks/" + taskA)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"p\"}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/analytics/dashboard").header("Authorization", bearer(TestJwtSupport.alice())).header("X-Organization-Id", orgB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You are not a member of this organization"));
        mockMvc.perform(get("/api/organizations/" + orgB + "/audit-logs").header("Authorization", bearer(TestJwtSupport.alice())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizations/" + orgB + "/members").header("Authorization", bearer(TestJwtSupport.alice())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/organizations/" + orgB).header("Authorization", bearer(TestJwtSupport.alice())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/organizations/" + orgA + "/members")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob@example.com\",\"role\":\"ORG_ADMIN\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/me").header("Authorization", bearer(TestJwtSupport.carol()))).andExpect(status().isOk());
        mockMvc.perform(post("/api/organizations/" + orgA + "/members")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carol@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/organizations/" + orgA + "/members")
                        .header("Authorization", bearer(TestJwtSupport.carol()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob@example.com\",\"role\":\"PROJECT_MANAGER\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/" + projectA + "/tasks/" + taskA)
                        .header("Authorization", bearer(TestJwtSupport.carol()))
                        .header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"steal\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/projects/" + projectA + "/tasks/" + taskA)
                        .header("Authorization", bearer(TestJwtSupport.carol()))
                        .header("X-Organization-Id", orgA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/me").header("Authorization", bearer(TestJwtSupport.dave()))).andExpect(status().isOk());
        mockMvc.perform(post("/api/organizations/" + orgA + "/members")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dave@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/projects").header("Authorization", bearer(TestJwtSupport.dave())).header("X-Organization-Id", orgA))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/" + projectA + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.dave()))
                        .header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "dave-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"dave task\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(TestJwtSupport.dave()))
                        .header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nope\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/dashboard").header("Authorization", bearer(TestJwtSupport.alice())).header("X-Organization-Id", orgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2));
        mockMvc.perform(get("/api/analytics/dashboard").header("Authorization", bearer(TestJwtSupport.bob())).header("X-Organization-Id", orgB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1));
        assertThat(stringRedisTemplate.opsForValue().get(AnalyticsService.cacheKey(orgA))).isNotNull();
        assertThat(stringRedisTemplate.opsForValue().get(AnalyticsService.cacheKey(orgB))).isNotNull();

        mockMvc.perform(post("/api/projects/" + projectA + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Alice key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Alice key"));
        mockMvc.perform(post("/api/projects/" + projectB + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.bob()))
                        .header("X-Organization-Id", orgB)
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bob key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Bob key"));

        mockMvc.perform(get("/api/projects/" + projectA + "/tasks")
                        .param("page", "-1")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/projects/" + projectA + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "plain")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("title=x"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/projects/" + projectA + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "unknown-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"ok\",\"organizationId\":" + orgB + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(orgA));
    }

    @Test
    void concurrentIdempotencyAndOptimisticLock() throws Exception {
        Long orgId = createOrg(TestJwtSupport.alice(), "Conc Org", "conc-org");
        long projectId = createProject(TestJwtSupport.alice(), orgId, "Conc");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> create = () -> mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                            .header("Authorization", bearer(TestJwtSupport.alice()))
                            .header("X-Organization-Id", orgId)
                            .header("Idempotency-Key", "concurrent-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Once\"}"))
                    .andReturn();
            Future<MvcResult> first = pool.submit(create);
            Future<MvcResult> second = pool.submit(create);
            int s1 = first.get().getResponse().getStatus();
            int s2 = second.get().getResponse().getStatus();
            assertThat(List.of(s1, s2)).contains(201);
            assertThat(s1 == 201 || s1 == 409).isTrue();
            assertThat(s2 == 201 || s2 == 409).isTrue();
            mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                            .header("Authorization", bearer(TestJwtSupport.alice()))
                            .header("X-Organization-Id", orgId))
                    .andExpect(jsonPath("$.totalElements").value(1));
        } finally {
            pool.shutdownNow();
        }

        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "lock-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lock\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> update = () -> mockMvc.perform(put("/api/projects/" + projectId + "/tasks/" + taskId)
                            .header("Authorization", bearer(TestJwtSupport.alice()))
                            .header("X-Organization-Id", orgId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"v1\",\"version\":0}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            Future<Integer> u1 = pool.submit(update);
            Future<Integer> u2 = pool.submit(update);
            List<Integer> statuses = new ArrayList<>(List.of(u1.get(), u2.get()));
            assertThat(statuses).contains(200);
            assertThat(statuses).contains(409);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void disabledUserCannotCallApi() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", bearer(TestJwtSupport.alice()))).andExpect(status().isOk());
        User alice = userRepository.findByKeycloakSub("alice-sub").orElseThrow();
        alice.setActive(false);
        userRepository.save(alice);
        try {
            mockMvc.perform(get("/api/me").header("Authorization", bearer(TestJwtSupport.alice())))
                    .andExpect(status().isForbidden());
        } finally {
            alice.setActive(true);
            userRepository.save(alice);
        }
    }

    @Test
    void workerFailsAfterMaxAttempts() {
        var analytics = org.mockito.Mockito.mock(com.tenant.management.service.AnalyticsService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(analytics).evict(99L);
        var jobs = new java.util.concurrent.ConcurrentHashMap<Long, com.tenant.management.entity.Job>();
        var repo = org.mockito.Mockito.mock(com.tenant.management.repository.JobRepository.class);
        com.tenant.management.entity.Job job = com.tenant.management.entity.Job.builder()
                .id(9L)
                .organizationId(99L)
                .type(com.tenant.management.service.JobService.INVALIDATE_ANALYTICS)
                .payload("{}")
                .status(com.tenant.management.entity.JobStatus.PENDING)
                .attempts(0)
                .maxAttempts(2)
                .availableAt(java.time.LocalDateTime.now().minusSeconds(1))
                .build();
        jobs.put(9L, job);
        org.mockito.Mockito.when(repo.findDue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> jobs.get(9L).getStatus() == com.tenant.management.entity.JobStatus.PENDING
                        && !jobs.get(9L).getAvailableAt().isAfter(java.time.LocalDateTime.now())
                        ? java.util.List.of(jobs.get(9L)) : java.util.List.of());
        org.mockito.Mockito.when(repo.claim(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    com.tenant.management.entity.Job current = jobs.get(9L);
                    if (current.getStatus() != com.tenant.management.entity.JobStatus.PENDING) {
                        return 0;
                    }
                    current.setStatus(com.tenant.management.entity.JobStatus.PROCESSING);
                    current.setAttempts(current.getAttempts() + 1);
                    return 1;
                });
        org.mockito.Mockito.when(repo.findById(9L)).thenAnswer(inv -> java.util.Optional.of(jobs.get(9L)));
        org.mockito.Mockito.when(repo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            com.tenant.management.entity.Job saved = inv.getArgument(0);
            jobs.put(saved.getId(), saved);
            return saved;
        });
        JobProcessor processor = new JobProcessor(repo, analytics,
                org.mockito.Mockito.mock(com.tenant.management.repository.TaskRepository.class),
                org.mockito.Mockito.mock(com.tenant.management.service.AuditService.class));
        processor.processDue();
        jobs.get(9L).setAvailableAt(java.time.LocalDateTime.now().minusSeconds(1));
        processor.processDue();
        assertThat(jobs.get(9L).getStatus()).isEqualTo(com.tenant.management.entity.JobStatus.FAILED);
        assertThat(jobs.get(9L).getAttempts()).isEqualTo(2);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long createOrg(String token, String name, String slug) throws Exception {
        mockMvc.perform(post("/api/organizations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(get("/api/organizations").header("Authorization", bearer(token))).andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (slug.equals(node.get("slug").asText())) {
                return node.get("id").asLong();
            }
        }
        throw new IllegalStateException("org not found");
    }

    private long createProject(String token, Long orgId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(token))
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createTask(String token, Long orgId, long projectId, String title, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(token))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
