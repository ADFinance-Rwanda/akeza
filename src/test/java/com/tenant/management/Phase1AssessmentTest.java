package com.tenant.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.service.AnalyticsService;
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

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class Phase1AssessmentTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void taskCollectionFiltersDueDateCriticalProjectStatusAndDashboard() throws Exception {
        Long orgId = createOrg(TestJwtSupport.alice(), "Phase1 Org", "phase1-org");
        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Board\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "p1-overdue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Overdue crit\",\"priority\":\"CRITICAL\",\"dueDate\":\"" + yesterday + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.dueDate").value(yesterday));
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "p1-future")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Later\",\"priority\":\"LOW\",\"status\":\"DONE\",\"dueDate\":\"" + tomorrow + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks")
                        .param("status", "TODO")
                        .param("priority", "CRITICAL")
                        .param("projectId", String.valueOf(projectId))
                        .param("overdue", "true")
                        .param("q", "Overdue")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Overdue crit"));

        mockMvc.perform(get("/api/tasks")
                        .param("dueAfter", yesterday)
                        .param("dueBefore", tomorrow)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/tasks")
                        .param("dueBefore", yesterday)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Overdue crit"));
        mockMvc.perform(get("/api/tasks")
                        .param("dueAfter", tomorrow)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Later"));

        mockMvc.perform(get("/api/me").header("Authorization", bearer(TestJwtSupport.carol()))).andExpect(status().isOk());
        long carolId = userId("carol@example.com", TestJwtSupport.carol());
        mockMvc.perform(post("/api/organizations/" + orgId + "/members")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carol@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/tasks")
                        .param("assigneeUserId", String.valueOf(carolId))
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        Long orgB = createOrg(TestJwtSupport.bob(), "Phase1 B", "phase1-b");
        mockMvc.perform(get("/api/tasks")
                        .param("projectId", String.valueOf(projectId))
                        .header("Authorization", bearer(TestJwtSupport.bob()))
                        .header("X-Organization-Id", orgB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgB))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/dashboard")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2))
                .andExpect(jsonPath("$.pendingTasks").value(1))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.overdueTasks").value(1))
                .andExpect(jsonPath("$.tasksByStatus.TODO").value(1))
                .andExpect(jsonPath("$.tasksByStatus.DONE").value(1))
                .andExpect(jsonPath("$.tasksByPriority.CRITICAL").value(1))
                .andExpect(jsonPath("$.createdTrend").isArray())
                .andExpect(jsonPath("$.completedTrend").isArray());

        assertThat(stringRedisTemplate.getExpire(AnalyticsService.cacheKey(orgId))).isGreaterThan(0);
    }

    @Test
    void superAdminCanReadOtherOrgAndUnknownApiIs404() throws Exception {
        Long orgB = createOrg(TestJwtSupport.bob(), "Erin Target", "erin-target");
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", bearer(TestJwtSupport.erin()))
                        .header("X-Organization-Id", orgB))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/missing-route")
                        .header("Authorization", bearer(TestJwtSupport.alice())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void flatTaskRoutesUseTheSameServiceAndStayTenantScoped() throws Exception {
        Long orgA = createOrg(TestJwtSupport.alice(), "Flat A", "flat-org-a");
        Long orgB = createOrg(TestJwtSupport.bob(), "Flat B", "flat-org-b");
        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Board\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        MvcResult created = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "flat-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + projectId + ",\"title\":\"Flat task\",\"priority\":\"LOW\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(orgA))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andReturn();
        long taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        long version = objectMapper.readTree(created.getResponse().getContentAsString()).get("version").asLong();

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Flat task"));
        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", bearer(TestJwtSupport.bob()))
                        .header("X-Organization-Id", orgB))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Flat renamed\",\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Flat renamed"));
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"stale\",\"version\":" + version + "}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", bearer(TestJwtSupport.bob()))
                        .header("X-Organization-Id", orgB))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA))
                .andExpect(status().isNotFound());
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

    private long userId(String email, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me").header("Authorization", bearer(token))).andReturn();
        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).get("user");
        if (email.equals(user.get("email").asText())) {
            return user.get("id").asLong();
        }
        throw new IllegalStateException("user not found");
    }
}
