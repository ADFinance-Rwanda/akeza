package com.tenant.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.dto.CreateTenantRequest;
import com.tenant.management.dto.UpdateTenantRequest;
import com.tenant.management.entity.TenantStatus;
import com.tenant.management.repository.MembershipRepository;
import com.tenant.management.repository.TenantRepository;
import com.tenant.management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
class TenantControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        membershipRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
        token = AuthTestHelper.registerAndGetToken(mockMvc, objectMapper, "owner@example.com");
    }

    @Test
    void shouldCreateAndFetchTenant() throws Exception {
        CreateTenantRequest request = CreateTenantRequest.builder()
                .name("Acme Corp")
                .slug("acme-corp")
                .description("Primary tenant")
                .build();

        mockMvc.perform(post("/api/tenants")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.slug").value("acme-corp"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/tenants").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/tenants/" + tenantRepository.findAll().get(0).getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acme-corp"));
    }

    @Test
    void shouldRejectBlankTenantUpdateAndMalformedBody() throws Exception {
        CreateTenantRequest request = CreateTenantRequest.builder()
                .name("Acme")
                .slug("acme")
                .build();

        MvcResult created = mockMvc.perform(post("/api/tenants")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/tenants/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/tenants")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request body"));
    }

    @Test
    void shouldRejectInvalidTenant() throws Exception {
        String payload = "{\"name\":\"\",\"slug\":\"INVALID SLUG\"}";

        mockMvc.perform(post("/api/tenants")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldRejectDuplicateSlug() throws Exception {
        CreateTenantRequest request = CreateTenantRequest.builder()
                .name("Acme")
                .slug("acme")
                .build();

        mockMvc.perform(post("/api/tenants")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tenants")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturnNotFoundForMissingTenant() throws Exception {
        mockMvc.perform(get("/api/tenants/999").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Tenant not found with id: 999"));
    }

    @Test
    void shouldUpdateAndDeleteTenant() throws Exception {
        CreateTenantRequest request = CreateTenantRequest.builder()
                .name("Acme")
                .slug("acme")
                .build();

        MvcResult created = mockMvc.perform(post("/api/tenants")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        UpdateTenantRequest update = UpdateTenantRequest.builder()
                .name("Acme Updated")
                .status(TenantStatus.SUSPENDED)
                .build();

        mockMvc.perform(put("/api/tenants/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Updated"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(delete("/api/tenants/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tenants/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }
}
