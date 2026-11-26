package com.converter.auth;

import com.converter.auth.dto.AuthDtos.AuthTokenResponse;
import com.converter.auth.dto.AuthDtos.LoginRequest;
import com.converter.auth.dto.AuthDtos.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_db")
            .withUsername("auth_app")
            .withPassword("auth_test_pw");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("platform.jwt.secret", () -> "integration-test-jwt-secret-key-at-least-32-bytes!");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullAuthLifecycleAndSecurityInvariants() throws Exception {
        String email = "alice." + UUID.randomUUID() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest(email, "Str0ngPass!", "Alice Moldovan");

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Request-ID", "auth-it-req-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-ID", "auth-it-req-1"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        AuthTokenResponse regResp = objectMapper.readValue(
                regResult.getResponse().getContentAsString(),
                AuthTokenResponse.class
        );
        assertThat(regResp.accessToken()).isNotBlank();

        // Duplicate email -> 409 RFC 9457
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        // Login success
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "Str0ngPass!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Login wrong password -> 401 RFC 9457
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "WrongPass!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // /me with valid JWT -> 200
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + regResp.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(regResp.user().id().toString()))
                .andExpect(jsonPath("$.email").value(email.toLowerCase()));

        // /me with spoofed X-User-Id but NO JWT -> 401
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-User-Id", regResp.user().id().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // /me with tampered JWT -> 401
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + regResp.accessToken() + "tamper"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Health & Readiness probes
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.checks.database").value("ok"));
    }
}
