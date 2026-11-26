package com.converter.gateway;

import com.converter.common.security.JwtTokenService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiGatewayIntegrationTest {

    private static HttpServer mockUpstream;
    private static final AtomicReference<String> observedUserIdHeader = new AtomicReference<>();
    private static final AtomicReference<String> observedRequestIdHeader = new AtomicReference<>();

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @BeforeAll
    static void startMockUpstream() throws Exception {
        mockUpstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockUpstream.createContext("/", exchange -> {
            observedUserIdHeader.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            observedRequestIdHeader.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
            String path = exchange.getRequestURI().getPath();
            String body = "/health".equals(path)
                    ? "{\"status\":\"ok\"}"
                    : "{\"proxiedPath\":\"" + path + "\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        mockUpstream.start();
    }

    @AfterAll
    static void stopMockUpstream() {
        if (mockUpstream != null) {
            mockUpstream.stop(0);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        String upstreamUrl = "http://127.0.0.1:" + mockUpstream.getAddress().getPort();
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("platform.jwt.secret", () -> "gateway-integration-test-secret-key-32-bytes-ok!!");
        registry.add("gateway.upstreams.auth-service-url", () -> upstreamUrl);
        registry.add("gateway.upstreams.rate-service-url", () -> upstreamUrl);
        registry.add("gateway.upstreams.conversion-service-url", () -> upstreamUrl);
        registry.add("gateway.upstreams.alert-service-url", () -> upstreamUrl);
        registry.add("gateway.upstreams.notification-service-url", () -> upstreamUrl);
        registry.add("gateway.rate-limit.default-requests-per-window", () -> 4);
        registry.add("gateway.rate-limit.window-seconds", () -> 60);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void failClosedAuthHeaderSanitizationPathTraversalRateLimitAndReadiness() throws Exception {
        // 1. Protected route without JWT -> 401 RFC 9457
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 2. Public route forwards cleanly and strips spoofed X-User-Id header!
        observedUserIdHeader.set("initial");
        mockMvc.perform(get("/api/v1/currencies")
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000001")
                        .header("X-Request-ID", "gw-trace-public-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "gw-trace-public-1"))
                .andExpect(jsonPath("$.proxiedPath").value("/api/v1/currencies"));
        assertThat(observedUserIdHeader.get()).isNull();
        assertThat(observedRequestIdHeader.get()).isEqualTo("gw-trace-public-1");

        // 3. Authenticated route injects verified X-User-Id (ignoring spoofed header)
        UUID realUser = UUID.randomUUID();
        String token = jwtTokenService.issueAccessToken(realUser, "real@example.com", "USER");
        mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", "spoofed-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proxiedPath").value("/api/v1/alerts"));
        assertThat(observedUserIdHeader.get()).isEqualTo(realUser.toString());

        // 4. Encoded path traversal -> 400 RFC 9457
        mockMvc.perform(get("/api/v1/currencies/%252e%252e/admin"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 5. Redis sliding-window rate limit triggers 429 + Retry-After on 5th request for same user
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 6. Health & aggregated readiness
        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.checks.redis").value("ok"))
                .andExpect(jsonPath("$.checks.auth-service").value("ok"));
    }
}
