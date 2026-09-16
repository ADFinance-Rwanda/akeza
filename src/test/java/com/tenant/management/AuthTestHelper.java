package com.tenant.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenant.management.dto.CreateUserRequest;
import com.tenant.management.dto.LoginRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class AuthTestHelper {

    static final String PASSWORD = "Password123";

    private AuthTestHelper() {
    }

    static String registerAndGetToken(MockMvc mockMvc, ObjectMapper objectMapper, String email) throws Exception {
        return registerAndGetToken(mockMvc, objectMapper, "Test", "User", email);
    }

    static String registerAndGetToken(MockMvc mockMvc, ObjectMapper objectMapper, String firstName, String lastName, String email)
            throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .password(PASSWORD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return bearer(objectMapper, result);
    }

    static String loginAndGetToken(MockMvc mockMvc, ObjectMapper objectMapper, String email) throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(PASSWORD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return bearer(objectMapper, result);
    }

    static Long userIdFromAuthResponse(ObjectMapper objectMapper, MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("user").get("id").asLong();
    }

    private static String bearer(ObjectMapper objectMapper, MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return "Bearer " + json.get("accessToken").asText();
    }
}
