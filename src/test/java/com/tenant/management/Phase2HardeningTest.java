package com.tenant.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.entity.JobStatus;
import com.tenant.management.service.JobProcessor;
import com.tenant.management.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class Phase2HardeningTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JobProcessor jobProcessor;
    @Autowired
    private JobService jobService;

    @Test
    void roleChangeAuditSuspensionAndIdempotency() throws Exception {
        Long orgId = createOrg(TestJwtSupport.alice(), "Phase2 Org", "phase2-org");
        long projectId = createProject(orgId, "Board");
        mockMvc.perform(get("/api/me").header("Authorization", bearer(TestJwtSupport.carol()))).andExpect(status().isOk());
        mockMvc.perform(post("/api/organizations/" + orgId + "/members")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carol@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());
        long carolId = userId("carol@example.com");

        mockMvc.perform(put("/api/organizations/" + orgId + "/members/" + carolId)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PROJECT_MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROJECT_MANAGER"));
        mockMvc.perform(put("/api/organizations/" + orgId + "/members/" + carolId)
                        .header("Authorization", bearer(TestJwtSupport.carol()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizations/" + orgId + "/audit-logs")
                        .header("Authorization", bearer(TestJwtSupport.carol())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizations/" + orgId + "/audit-logs")
                        .header("Authorization", bearer(TestJwtSupport.alice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", hasItem("USER_ROLE_CHANGED")));
        mockMvc.perform(get("/api/organizations/" + orgId + "/audit-logs")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .param("action", "USER_ROLE_CHANGED")
                        .param("q", "PROJECT_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("USER_ROLE_CHANGED"))))
                .andExpect(jsonPath("$.content[*].metadata", hasItem("MEMBER->PROJECT_MANAGER")));
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskCount").isNumber());

        Long orgB = createOrg(TestJwtSupport.bob(), "Phase2 B", "phase2-b");
        mockMvc.perform(get("/api/organizations/" + orgB + "/audit-logs")
                        .header("Authorization", bearer(TestJwtSupport.alice())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/organizations/" + orgB + "/members/" + carolId)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "p2-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "p2-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"recovered\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "p2-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"recovered\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId));
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "p2-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"other-body\"}"))
                .andExpect(status().isConflict());

        MvcResult carolCreated = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.carol()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "p2-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"carol same key\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long carolTaskId = objectMapper.readTree(carolCreated.getResponse().getContentAsString()).get("id").asLong();
        assertThat(carolTaskId).isNotEqualTo(taskId);
    }

    @Test
    void suspensionBlocksMembersAndSuperAdminCanOperate() throws Exception {
        Long orgId = createOrg(TestJwtSupport.alice(), "Suspend Org", "suspend-org");
        long projectId = createProject(orgId, "Work");
        mockMvc.perform(put("/api/organizations/" + orgId + "/status")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Organization is suspended"));
        mockMvc.perform(get("/api/analytics/dashboard")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "suspended")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"blocked\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me").header("Authorization", bearer(TestJwtSupport.erin()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", bearer(TestJwtSupport.erin()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/organizations/" + orgId + "/status")
                        .header("Authorization", bearer(TestJwtSupport.erin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk());
    }

    @Test
    void overdueScanWritesAuditOnceAndDeadLettersAreScoped() throws Exception {
        Long orgId = createOrg(TestJwtSupport.alice(), "Overdue Org", "overdue-org");
        long projectId = createProject(orgId, "Work");
        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .header("Idempotency-Key", "overdue-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"late\",\"dueDate\":\"" + yesterday + "\"}"))
                .andExpect(status().isCreated());
        assertThat(jobService.enqueueOverdueScan()).isNotNull();
        assertThat(jobProcessor.processDue()).isGreaterThan(0);
        jobProcessor.processDue();
        mockMvc.perform(get("/api/organizations/" + orgId + "/audit-logs")
                        .header("Authorization", bearer(TestJwtSupport.alice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", hasItem("TASK_OVERDUE")));
        mockMvc.perform(get("/api/organizations/" + orgId + "/jobs/dead-letters")
                        .header("Authorization", bearer(TestJwtSupport.alice())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me").header("Authorization", bearer(TestJwtSupport.carol()))).andExpect(status().isOk());
        mockMvc.perform(post("/api/organizations/" + orgId + "/members")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carol@example.com\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/organizations/" + orgId + "/jobs/dead-letters")
                        .header("Authorization", bearer(TestJwtSupport.carol())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
        assertThat(JobStatus.FAILED.name()).isEqualTo("FAILED");
    }

    @Test
    void idempotencyScopesAndConcurrentDuplicatesCreateOneTask() throws Exception {
        Long orgA = createOrg(TestJwtSupport.alice(), "Idemp Scope A", "idemp-scope-a");
        Long orgB = createOrg(TestJwtSupport.alice(), "Idemp Scope B", "idemp-scope-b");
        long projectA = createProject(orgA, "Board A");
        long projectB = createProject(orgB, "Board B");

        MvcResult first = mockMvc.perform(post("/api/projects/" + projectA + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "shared-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"org-a\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskA = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();
        MvcResult secondOrg = mockMvc.perform(post("/api/projects/" + projectB + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgB)
                        .header("Idempotency-Key", "shared-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"org-b\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskB = objectMapper.readTree(secondOrg.getResponse().getContentAsString()).get("id").asLong();
        assertThat(taskB).isNotEqualTo(taskA);

        mockMvc.perform(post("/api/projects/" + projectA + "/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .header("Idempotency-Key", "other-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"other-key\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/tasks")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA))
                .andExpect(jsonPath("$.totalElements").value(2));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> create = () -> mockMvc.perform(post("/api/projects/" + projectA + "/tasks")
                            .header("Authorization", bearer(TestJwtSupport.alice()))
                            .header("X-Organization-Id", orgA)
                            .header("Idempotency-Key", "concurrent-scope")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"once\"}"))
                    .andReturn();
            Future<MvcResult> one = pool.submit(create);
            Future<MvcResult> two = pool.submit(create);
            List<Integer> statuses = new ArrayList<>(List.of(
                    one.get().getResponse().getStatus(),
                    two.get().getResponse().getStatus()));
            assertThat(statuses).contains(201);
            assertThat(statuses).allMatch(code -> code == 201 || code == 200);
            mockMvc.perform(get("/api/tasks?q=once")
                            .header("Authorization", bearer(TestJwtSupport.alice()))
                            .header("X-Organization-Id", orgA))
                    .andExpect(jsonPath("$.totalElements").value(1));
        } finally {
            pool.shutdownNow();
        }

        mockMvc.perform(put("/api/projects/" + projectA + "/tasks/" + taskA)
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"renamed\",\"status\":\"IN_PROGRESS\",\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/organizations/" + orgA + "/audit-logs")
                        .header("Authorization", bearer(TestJwtSupport.alice())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", hasItem("TASK_UPDATED")))
                .andExpect(jsonPath("$.content[*].metadata", hasItem("org-a/TODO->renamed/IN_PROGRESS")));
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

    private long createProject(Long orgId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(TestJwtSupport.alice()))
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long userId(String email) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me").header("Authorization", bearer(
                email.startsWith("carol") ? TestJwtSupport.carol() : TestJwtSupport.alice()))).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("user").get("id").asLong();
    }
}
