package com.tenant.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.dto.CreateMembershipRequest;
import com.tenant.management.dto.CreateTenantRequest;
import com.tenant.management.entity.MemberRole;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

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

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void outsiderCannotAccessTenantAndMemberCannotManageIt() throws Exception {
        String ownerToken = AuthTestHelper.registerAndGetToken(mockMvc, objectMapper, "owner@example.com");
        Long tenantId = createTenant(ownerToken, "Acme", "acme");

        String outsiderToken = AuthTestHelper.registerAndGetToken(mockMvc, objectMapper, "outsider@example.com");
        mockMvc.perform(get("/api/tenants/" + tenantId).header(HttpHeaders.AUTHORIZATION, outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        String memberToken = AuthTestHelper.registerAndGetToken(mockMvc, objectMapper, "Member", "Two", "member@example.com");
        Long memberId = userRepository.findByEmailIgnoreCase("member@example.com").orElseThrow().getId();

        mockMvc.perform(post("/api/tenants/" + tenantId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateMembershipRequest.builder()
                                .userId(memberId)
                                .role(MemberRole.MEMBER)
                                .build())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tenants/" + tenantId).header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/tenants/" + tenantId)
                        .header(HttpHeaders.AUTHORIZATION, memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/tenants/" + tenantId).header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isForbidden());
    }

    private Long createTenant(String token, String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateTenantRequest.builder()
                                .name(name)
                                .slug(slug)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
