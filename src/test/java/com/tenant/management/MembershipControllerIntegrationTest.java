package com.tenant.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.dto.CreateMembershipRequest;
import com.tenant.management.dto.CreateTenantRequest;
import com.tenant.management.dto.CreateUserRequest;
import com.tenant.management.dto.UpdateMembershipRequest;
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
class MembershipControllerIntegrationTest {

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

    private String ownerToken;
    private Long ownerId;

    @BeforeEach
    void setUp() throws Exception {
        membershipRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
        ownerToken = AuthTestHelper.registerAndGetToken(mockMvc, objectMapper, "Owner", "One", "owner@example.com");
        ownerId = userRepository.findByEmailIgnoreCase("owner@example.com").orElseThrow().getId();
    }

    @Test
    void shouldAddListUpdateAndRemoveMembers() throws Exception {
        Long tenantId = createTenant("Acme", "acme", ownerToken);
        Long memberId = createUser(ownerToken, "Member", "Two", "member@example.com");

        mockMvc.perform(post("/api/tenants/" + tenantId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateMembershipRequest.builder()
                                .userId(memberId)
                                .role(MemberRole.MEMBER)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userEmail").value("member@example.com"));

        mockMvc.perform(get("/api/tenants/" + tenantId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/users/" + ownerId + "/tenants")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tenantSlug").value("acme"));

        mockMvc.perform(put("/api/tenants/" + tenantId + "/members/" + memberId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateMembershipRequest.builder()
                                .role(MemberRole.ADMIN)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(delete("/api/tenants/" + tenantId + "/members/" + memberId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tenants/" + tenantId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void shouldRejectDuplicateMembershipAndLastOwnerRemoval() throws Exception {
        Long tenantId = createTenant("Acme", "acme", ownerToken);

        CreateMembershipRequest ownerMembership = CreateMembershipRequest.builder()
                .userId(ownerId)
                .role(MemberRole.OWNER)
                .build();

        mockMvc.perform(post("/api/tenants/" + tenantId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerMembership)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/tenants/" + tenantId + "/members/" + ownerId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot remove the last owner from a tenant"));

        mockMvc.perform(put("/api/tenants/" + tenantId + "/members/" + ownerId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateMembershipRequest.builder()
                                .role(MemberRole.MEMBER)
                                .build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot change role of the last owner in a tenant"));

        mockMvc.perform(delete("/api/users/" + ownerId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundForMissingTenantMembers() throws Exception {
        mockMvc.perform(get("/api/tenants/999/members").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/users/999/tenants").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundForMissingMembership() throws Exception {
        Long tenantId = createTenant("Acme", "acme", ownerToken);
        Long userId = createUser(ownerToken, "Jane", "Doe", "jane@example.com");

        mockMvc.perform(delete("/api/tenants/" + tenantId + "/members/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private Long createTenant(String name, String slug, String token) throws Exception {
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

    private Long createUser(String token, String firstName, String lastName, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateUserRequest.builder()
                                .firstName(firstName)
                                .lastName(lastName)
                                .email(email)
                                .password(AuthTestHelper.PASSWORD)
                                .build())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
