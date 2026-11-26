package com.converter.conversion;

import com.converter.common.dto.ConversionRequestDto;
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
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
class ConversionServiceIntegrationTest {

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
            {"code": "USD", "name": "US Dollar", "nominal": 1, "rate": 17.825000},
            {"code": "EUR", "name": "Euro", "nominal": 1, "rate": 19.450000},
            {"code": "JPY", "name": "Japanese Yen", "nominal": 100, "rate": 11.850000}
          ]
        }
        """;

    private static HttpServer mockRateService;
    private static final AtomicBoolean failRateService = new AtomicBoolean(false);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("conversions_db")
            .withUsername("conversion_app")
            .withPassword("conversion_test_pw");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @BeforeAll
    static void startMockRateService() throws Exception {
        mockRateService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockRateService.createContext("/api/v1/rates", exchange -> {
            if (failRateService.get()) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
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
        registry.add("conversion.rate-service-url", () -> "http://127.0.0.1:" + mockRateService.getAddress().getPort());
        registry.add("platform.jwt.secret", () -> "conversion-integration-test-secret-key-32-bytes!!");
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
    void bigDecimalPrecisionOutboxEventGracefulDegradationAndIdor() throws Exception {
        failRateService.set(false);

        // Bind a temporary test queue to currency.events to verify Outbox -> RabbitMQ delivery
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());
        TopicExchange exchange = new TopicExchange(RabbitTopologyConstants.EXCHANGE_EVENTS, true, false);
        Queue testQueue = new Queue("test.conversion.events", false, false, true);
        admin.declareExchange(exchange);
        admin.declareQueue(testQueue);
        admin.declareBinding(BindingBuilder.bind(testQueue).to(exchange).with(RabbitTopologyConstants.ROUTING_CONVERSION_COMPLETED));

        // 1. Anonymous USD -> JPY (nominal=100) exact DECIMAL128 precision check
        ConversionRequestDto jpyReq = new ConversionRequestDto(
                new BigDecimal("100.00"), "USD", "JPY", LocalDate.of(2026, 9, 25)
        );
        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jpyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(15042.1941))
                .andExpect(jsonPath("$.effectiveRate").value(150.421941))
                .andExpect(jsonPath("$.offline").value(false));

        // 2. Authenticated USD -> EUR persists history & publishes ConversionCompleted via Outbox -> RabbitMQ
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String tokenA = jwtTokenService.issueAccessToken(userA, "a@example.com", "USER");
        String tokenB = jwtTokenService.issueAccessToken(userB, "b@example.com", "USER");

        ConversionRequestDto eurReq = new ConversionRequestDto(
                new BigDecimal("100.00"), "USD", "EUR", LocalDate.of(2026, 9, 25)
        );
        MvcResult convRes = mockMvc.perform(post("/api/v1/convert")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("X-Request-ID", "conv-e2e-trace-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eurReq)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Conversion-Id"))
                .andExpect(jsonPath("$.convertedAmount").value(91.6452))
                .andReturn();

        String conversionId = convRes.getResponse().getHeader("X-Conversion-Id");
        assertThat(conversionId).isNotBlank();

        // Verify RabbitMQ received ConversionCompleted event
        Message msg = rabbitTemplate.receive(testQueue.getName(), 5000);
        assertThat(msg).isNotNull();
        PlatformEvent published = objectMapper.readValue(msg.getBody(), PlatformEvent.class);
        assertThat(published.eventType()).isEqualTo(RabbitTopologyConstants.EVENT_CONVERSION_COMPLETED);
        assertThat(published.userId()).isEqualTo(userA);
        assertThat(published.correlationId()).isEqualTo("conv-e2e-trace-1");

        // 3. Owner reads own history -> 200; User B tries to read User A's conversion -> 403 IDOR!
        mockMvc.perform(get("/api/v1/conversions/" + conversionId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversionId));

        mockMvc.perform(get("/api/v1/conversions/" + conversionId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 4. Graceful degradation: rate-service goes down -> still converts from local snapshot with offline=true
        failRateService.set(true);
        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eurReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offline").value(true))
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.convertedAmount").value(91.6452));

        // 5. Health & readiness
        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.database").value("ok"));
    }
}
