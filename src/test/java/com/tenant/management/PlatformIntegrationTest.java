package com.tenant.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class PlatformIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JobProcessor jobProcessor;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/does-not-exist").with(alice())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnonymousApiAccess() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void tenantIsolationOptimisticLockIdempotencyAndRbac() throws Exception {
        mockMvc.perform(post("/api/organizations").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Org A\",\"slug\":\"org-a\"}"))
                .andExpect(status().isCreated());
        Long orgA = orgId("org-a", alice());

        mockMvc.perform(post("/api/organizations").with(bob())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Org B\",\"slug\":\"org-b\"}"))
                .andExpect(status().isCreated());
        Long orgB = orgId("org-b", bob());

        mockMvc.perform(get("/api/organizations/" + orgB).with(alice()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects").with(alice()).header("X-Organization-Id", orgB))
                .andExpect(status().isForbidden());

        MvcResult projectA = mockMvc.perform(post("/api/projects").with(alice()).header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project A\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectAId = objectMapper.readTree(projectA.getResponse().getContentAsString()).get("id").asLong();

        MvcResult projectB = mockMvc.perform(post("/api/projects").with(bob()).header("X-Organization-Id", orgB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project B\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectBId = objectMapper.readTree(projectB.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/projects/" + projectBId).with(alice()).header("X-Organization-Id", orgA))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/999999").with(alice()).header("X-Organization-Id", orgA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/me").with(alice()).header("X-Request-Id", "corr-audit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                .andExpect(header().string("X-Request-Id", "corr-audit-1"));

        String taskBody = "{\"title\":\"Task A\",\"priority\":\"HIGH\"}";
        MvcResult created = mockMvc.perform(post("/api/projects/" + projectAId + "/tasks")
                        .with(alice()).header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "ABC123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        JsonNode task = objectMapper.readTree(created.getResponse().getContentAsString());
        long taskId = task.get("id").asLong();

        mockMvc.perform(post("/api/projects/" + projectAId + "/tasks")
                        .with(alice()).header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "ABC123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId));

        mockMvc.perform(post("/api/projects/" + projectAId + "/tasks")
                        .with(alice()).header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "ABC123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Different body\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/projects/" + projectAId + "/tasks")
                        .with(alice()).header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/projects/" + projectAId + "/tasks").with(alice()).header("X-Organization-Id", orgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/projects/" + projectBId + "/tasks/" + taskId).with(bob()).header("X-Organization-Id", orgB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks").with(alice()).header("X-Organization-Id", orgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
        mockMvc.perform(get("/api/tasks").with(alice()).header("X-Organization-Id", orgB))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/" + projectBId + "/tasks/" + taskId).with(bob()).header("X-Organization-Id", orgB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hacked\",\"version\":0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/projects/" + projectBId + "/tasks/" + taskId).with(bob()).header("X-Organization-Id", orgB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/projects/" + projectAId + "/tasks/" + taskId)
                        .with(alice()).header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Task A2\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/projects/" + projectAId + "/tasks/" + taskId)
                        .with(alice()).header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Stale\",\"version\":0}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/projects/" + projectAId + "/tasks/" + taskId)
                        .with(alice()).header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No version\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/analytics/dashboard").with(alice()).header("X-Organization-Id", orgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.pendingTasks").value(1))
                .andExpect(jsonPath("$.completedTasks").value(0))
                .andExpect(jsonPath("$.overdueTasks").value(0))
                .andExpect(jsonPath("$.tasksByPriority.HIGH").value(1))
                .andExpect(jsonPath("$.createdTrend").isArray());

        assertThat(stringRedisTemplate.opsForValue().get(AnalyticsService.cacheKey(orgA))).isNotNull();
        assertThat(stringRedisTemplate.getExpire(AnalyticsService.cacheKey(orgA))).isGreaterThan(0);

        mockMvc.perform(get("/api/organizations/" + orgA + "/audit-logs").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(put("/api/organizations/" + orgA + "/audit-logs").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/organizations/" + orgA + "/audit-logs").with(alice()))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/api/projects").with(alice()).header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        assertThat(jobProcessor.processDue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void memberCannotCreateProjectCanCreateTask() throws Exception {
        mockMvc.perform(post("/api/organizations").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RBAC Org\",\"slug\":\"rbac-org\"}"))
                .andExpect(status().isCreated());
        Long orgId = orgId("rbac-org", alice());

        mockMvc.perform(get("/api/me").with(carol())).andExpect(status().isOk());
        mockMvc.perform(post("/api/organizations/" + orgId + "/members").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carol@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects").with(carol()).header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());

        MvcResult project = mockMvc.perform(post("/api/projects").with(alice()).header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Shared\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks").with(carol()).header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "carol-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Member task\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void taskFilterPaginationAndRateLimit() throws Exception {
        mockMvc.perform(post("/api/organizations").with(alice())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Filter Org\",\"slug\":\"filter-org\"}"))
                .andExpect(status().isCreated());
        Long orgId = orgId("filter-org", alice());
        MvcResult project = mockMvc.perform(post("/api/projects").with(alice()).header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Filter Project\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks").with(alice()).header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "todo-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Alpha\",\"status\":\"TODO\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks").with(alice()).header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "done-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Beta\",\"status\":\"DONE\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .param("status", "DONE").param("q", "Beta").param("page", "0").param("size", "1")
                        .with(alice()).header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Beta"))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .param("page", "5").param("size", "10")
                        .with(alice()).header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks").with(alice()).header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "rl-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Gamma\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks").with(alice()).header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "rl-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Delta\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks").with(alice()).header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "rl-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Epsilon\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks").with(alice()).header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "rl-6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Zeta\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void workerRetriesUntilSuccess() {
        com.tenant.management.service.AnalyticsService analytics = org.mockito.Mockito.mock(com.tenant.management.service.AnalyticsService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .doThrow(new RuntimeException("boom"))
                .doNothing()
                .when(analytics).evict(42L);

        var jobs = new java.util.concurrent.ConcurrentHashMap<Long, com.tenant.management.entity.Job>();
        var repo = org.mockito.Mockito.mock(com.tenant.management.repository.JobRepository.class);
        com.tenant.management.entity.Job job = com.tenant.management.entity.Job.builder()
                .id(1L)
                .organizationId(42L)
                .type(com.tenant.management.service.JobService.INVALIDATE_ANALYTICS)
                .payload("{}")
                .status(com.tenant.management.entity.JobStatus.PENDING)
                .attempts(0)
                .maxAttempts(5)
                .availableAt(java.time.LocalDateTime.now().minusSeconds(1))
                .build();
        jobs.put(1L, job);

        org.mockito.Mockito.when(repo.findDue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> jobs.get(1L).getStatus() == com.tenant.management.entity.JobStatus.PENDING
                        && !jobs.get(1L).getAvailableAt().isAfter(java.time.LocalDateTime.now())
                        ? java.util.List.of(jobs.get(1L)) : java.util.List.of());
        org.mockito.Mockito.when(repo.claim(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    com.tenant.management.entity.Job current = jobs.get(1L);
                    if (current.getStatus() != com.tenant.management.entity.JobStatus.PENDING) {
                        return 0;
                    }
                    current.setStatus(com.tenant.management.entity.JobStatus.PROCESSING);
                    current.setAttempts(current.getAttempts() + 1);
                    return 1;
                });
        org.mockito.Mockito.when(repo.findById(1L)).thenAnswer(inv -> java.util.Optional.of(jobs.get(1L)));
        org.mockito.Mockito.when(repo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            com.tenant.management.entity.Job saved = inv.getArgument(0);
            jobs.put(saved.getId(), saved);
            return saved;
        });

        JobProcessor processor = new JobProcessor(repo, analytics,
                org.mockito.Mockito.mock(com.tenant.management.repository.TaskRepository.class),
                org.mockito.Mockito.mock(com.tenant.management.service.AuditService.class));
        processor.processDue();
        assertThat(jobs.get(1L).getStatus()).isEqualTo(com.tenant.management.entity.JobStatus.PENDING);
        jobs.get(1L).setAvailableAt(java.time.LocalDateTime.now().minusSeconds(1));
        processor.processDue();
        assertThat(jobs.get(1L).getStatus()).isEqualTo(com.tenant.management.entity.JobStatus.PENDING);
        jobs.get(1L).setAvailableAt(java.time.LocalDateTime.now().minusSeconds(1));
        processor.processDue();
        assertThat(jobs.get(1L).getStatus()).isEqualTo(com.tenant.management.entity.JobStatus.DONE);
        assertThat(jobs.get(1L).getAttempts()).isEqualTo(3);
    }

    @Test
    void workerExhaustedRetriesAreDeadLettered() {
        com.tenant.management.service.AnalyticsService analytics = org.mockito.Mockito.mock(com.tenant.management.service.AnalyticsService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("permanent"))
                .when(analytics).evict(7L);

        var jobs = new java.util.concurrent.ConcurrentHashMap<Long, com.tenant.management.entity.Job>();
        var repo = org.mockito.Mockito.mock(com.tenant.management.repository.JobRepository.class);
        com.tenant.management.entity.Job job = com.tenant.management.entity.Job.builder()
                .id(9L)
                .organizationId(7L)
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
        assertThat(jobs.get(9L).getStatus()).isEqualTo(com.tenant.management.entity.JobStatus.PENDING);
        jobs.get(9L).setAvailableAt(java.time.LocalDateTime.now().minusSeconds(1));
        processor.processDue();
        assertThat(jobs.get(9L).getStatus()).isEqualTo(com.tenant.management.entity.JobStatus.FAILED);
        assertThat(jobs.get(9L).getDeadLetteredAt()).isNotNull();
        assertThat(jobs.get(9L).getAttempts()).isEqualTo(2);
        assertThat(jobs.get(9L).getLastError()).contains("permanent");
    }

    private Long orgId(String slug, RequestPostProcessor user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/organizations").with(user)).andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (slug.equals(node.get("slug").asText())) {
                return node.get("id").asLong();
            }
        }
        throw new IllegalStateException("org not found");
    }

    private RequestPostProcessor alice() {
        return jwt().jwt(jwt -> jwt.subject("alice-sub")
                .claim("email", "alice@example.com")
                .claim("given_name", "Alice")
                .claim("family_name", "Admin"));
    }

    private RequestPostProcessor bob() {
        return jwt().jwt(jwt -> jwt.subject("bob-sub")
                .claim("email", "bob@example.com")
                .claim("given_name", "Bob")
                .claim("family_name", "Builder"));
    }

    private RequestPostProcessor carol() {
        return jwt().jwt(jwt -> jwt.subject("carol-sub")
                .claim("email", "carol@example.com")
                .claim("given_name", "Carol")
                .claim("family_name", "Member"));
    }
}
