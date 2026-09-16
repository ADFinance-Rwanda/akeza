package com.tenant.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.dto.CreateUserRequest;
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
class UserControllerIntegrationTest {

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
        token = AuthTestHelper.registerAndGetToken(mockMvc, objectMapper, "Jane", "Doe", "jane@example.com");
    }

    @Test
    void shouldCreateAndFetchUser() throws Exception {
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("jane@example.com"));

        mockMvc.perform(get("/api/users/" + userRepository.findAll().get(0).getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void shouldRejectInvalidUser() throws Exception {
        String payload = "{\"firstName\":\"\",\"lastName\":\"Doe\",\"email\":\"invalid-email\",\"password\":\"short\"}";

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .password(AuthTestHelper.PASSWORD)
                .build();

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturnNotFoundForMissingUser() throws Exception {
        mockMvc.perform(get("/api/users/999").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldUpdateAndDeleteUser() throws Exception {
        Long id = userRepository.findAll().get(0).getId();

        mockMvc.perform(put("/api/users/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Janet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Janet"));

        mockMvc.perform(delete("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        token = AuthTestHelper.registerAndGetToken(mockMvc, objectMapper, "other@example.com");
        mockMvc.perform(get("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }
}
