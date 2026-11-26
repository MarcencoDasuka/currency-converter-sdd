package com.converter.alert;

import com.converter.alert.dto.AlertDtos.AlertResponse;
import com.converter.alert.dto.AlertDtos.CreateAlertRequest;
import com.converter.common.events.PlatformEvent;
import com.converter.common.events.RabbitTopologyConstants;
import com.converter.common.security.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AlertServiceIntegrationTest {

    private static final String MOCK_RATES_JSON = """
        {
          "rateDate": "2026-09-25",
          "requestedDate": "2026-09-25",
          "source": "National Bank of Moldova",
          "cached": false,
          "offline": false,
          "rollbackDaysApplied": 0,
          "currencies": [
            {"code": "MDL", "name": "Moldovan Leu", "nominal": 1, "rate": 1.000000},
            {"code": "USD", "name": "US Dollar", "nominal": 1, "rate": 18.500000},
            {"code": "EUR", "name": "Euro", "nominal": 1, "rate": 19.500000}
          ]
        }
        """;

    private static HttpServer mockRateService;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("alerts_db")
            .withUsername("alert_app")
            .withPassword("alert_test_pw");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @BeforeAll
    static void startMockRateService() throws Exception {
        mockRateService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockRateService.createContext("/api/v1/rates", exchange -> {
            byte[] bytes = MOCK_RATES_JSON.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        mockRateService.start();
    }

    @AfterAll
    static void stopMockRateService() {
        if (mockRateService != null) {
            mockRateService.stop(0);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
        registry.add("alert.rate-service-url", () -> "http://127.0.0.1:" + mockRateService.getAddress().getPort());
        registry.add("platform.jwt.secret", () -> "alert-integration-test-secret-key-32-bytes-long!!");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void alertCrudIdorProtectionAndAtomicOutboxTrigger() throws Exception {
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());
        TopicExchange exchange = new TopicExchange(RabbitTopologyConstants.EXCHANGE_EVENTS, true, false);
        Queue testQueue = new Queue("test.alert.events", false, false, true);
        admin.declareExchange(exchange);
        admin.declareQueue(testQueue);
        admin.declareBinding(BindingBuilder.bind(testQueue).to(exchange).with(RabbitTopologyConstants.ROUTING_ALERT_TRIGGERED));

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String tokenA = jwtTokenService.issueAccessToken(userA, "alice@example.com", "USER");
        String tokenB = jwtTokenService.issueAccessToken(userB, "bob@example.com", "USER");

        // Create alert: EUR -> MDL ABOVE 19.000000 (current rate is 19.500000 -> will trigger!)
        CreateAlertRequest req = new CreateAlertRequest("EUR", "MDL", "ABOVE", new BigDecimal("19.000000"));
        MvcResult createRes = mockMvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        AlertResponse created = objectMapper.readValue(createRes.getResponse().getContentAsString(), AlertResponse.class);

        // User B cannot read or delete User A's alert (IDOR -> 403)
        mockMvc.perform(get("/api/v1/alerts/" + created.id())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(delete("/api/v1/alerts/" + created.id())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Evaluate triggers alert once and publishes RateAlertTriggered via Outbox -> RabbitMQ
        mockMvc.perform(post("/api/v1/alerts/evaluate")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("X-Request-ID", "alert-eval-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedCount").value(1))
                .andExpect(jsonPath("$.triggeredCount").value(1));

        // Second evaluate finds 0 active alerts (idempotent — no double trigger)
        mockMvc.perform(post("/api/v1/alerts/evaluate")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedCount").value(0))
                .andExpect(jsonPath("$.triggeredCount").value(0));

        Message msg = rabbitTemplate.receive(testQueue.getName(), 5000);
        assertThat(msg).isNotNull();
        PlatformEvent published = objectMapper.readValue(msg.getBody(), PlatformEvent.class);
        assertThat(published.eventType()).isEqualTo(RabbitTopologyConstants.EVENT_RATE_ALERT_TRIGGERED);
        assertThat(published.userId()).isEqualTo(userA);

        // Owner deletes alert -> 204
        mockMvc.perform(delete("/api/v1/alerts/" + created.id())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/ready")).andExpect(status().isOk());
    }
}
