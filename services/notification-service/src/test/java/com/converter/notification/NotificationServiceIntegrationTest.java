package com.converter.notification;

import com.converter.common.events.PlatformEvent;
import com.converter.common.events.RabbitTopologyConstants;
import com.converter.common.security.JwtTokenService;
import com.converter.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NotificationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notifications_db")
            .withUsername("notification_app")
            .withPassword("notif_test_pw");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
        registry.add("platform.jwt.secret", () -> "notif-integration-test-secret-key-32-bytes-long!!");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void idempotentDeliveryPoisonMessageDlqIdorAndReadiness() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        PlatformEvent event = new PlatformEvent(
                eventId,
                RabbitTopologyConstants.EVENT_CONVERSION_COMPLETED,
                userA,
                "notif-trace-1",
                Map.of(
                        "amount", "100.00",
                        "sourceCurrency", "USD",
                        "convertedAmount", "91.6452",
                        "targetCurrency", "EUR",
                        "effectiveRate", "0.916452",
                        "rateDate", "2026-09-25"
                ),
                Instant.now()
        );
        byte[] validBody = objectMapper.writeValueAsBytes(event);
        Message msg = MessageBuilder.withBody(validBody).setContentType(MessageProperties.CONTENT_TYPE_JSON).build();

        // Publish exact same event_id TWICE -> must be persisted only ONCE
        rabbitTemplate.send(RabbitTopologyConstants.EXCHANGE_EVENTS, RabbitTopologyConstants.ROUTING_CONVERSION_COMPLETED, msg);
        rabbitTemplate.send(RabbitTopologyConstants.EXCHANGE_EVENTS, RabbitTopologyConstants.ROUTING_CONVERSION_COMPLETED, msg);

        // Wait up to 5s for async consumption
        long deadline = System.currentTimeMillis() + 5000;
        while (repository.findAll().stream().noneMatch(n -> eventId.equals(n.getEventId())) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        Thread.sleep(300);

        long matchingCount = repository.findAll().stream().filter(n -> eventId.equals(n.getEventId())).count();
        assertThat(matchingCount).isEqualTo(1);
        UUID notificationId = repository.findAll().stream()
                .filter(n -> eventId.equals(n.getEventId()))
                .findFirst().orElseThrow().getId();

        // Publish poison malformed JSON -> must be rejected to DLQ without crashing consumer
        Message poison = MessageBuilder
                .withBody("{not-valid-json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(RabbitTopologyConstants.EXCHANGE_EVENTS, RabbitTopologyConstants.ROUTING_CONVERSION_COMPLETED, poison);

        Message dlqMessage = rabbitTemplate.receive(RabbitTopologyConstants.QUEUE_NOTIFICATIONS_DLQ, 5000);
        assertThat(dlqMessage).isNotNull();
        assertThat(new String(dlqMessage.getBody(), StandardCharsets.UTF_8)).isEqualTo("{not-valid-json");

        // Owner reads notifications & marks read -> 200; User B -> 403 IDOR
        String tokenA = jwtTokenService.issueAccessToken(userA, "alice@example.com", "USER");
        String tokenB = jwtTokenService.issueAccessToken(userB, "bob@example.com", "USER");

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$[0].read").value(false));

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.database").value("ok"))
                .andExpect(jsonPath("$.checks.rabbitmq").value("ok"));
    }
}
