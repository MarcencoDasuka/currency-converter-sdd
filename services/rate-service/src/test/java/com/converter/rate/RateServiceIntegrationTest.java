package com.converter.rate;

import com.converter.rate.repository.ExchangeRateRepository;
import com.converter.rate.service.ExchangeRateService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateServiceIntegrationTest {

    private static final String SAMPLE_XML = """
        <?xml version="1.0" encoding="utf-8"?>
        <ValCurs Date="25.09.2026" name="Official exchange rates">
            <Valute ID="47"><NumCode>840</NumCode><CharCode>USD</CharCode><Nominal>1</Nominal><Name>US Dollar</Name><Value>17.8250</Value></Valute>
            <Valute ID="44"><NumCode>978</NumCode><CharCode>EUR</CharCode><Nominal>1</Nominal><Name>Euro</Name><Value>19.4500</Value></Valute>
            <Valute ID="35"><NumCode>946</NumCode><CharCode>RON</CharCode><Nominal>1</Nominal><Name>Romanian Leu</Name><Value>3.9100</Value></Valute>
            <Valute ID="36"><NumCode>980</NumCode><CharCode>UAH</CharCode><Nominal>1</Nominal><Name>Ukrainian Hryvnia</Name><Value>0.4350</Value></Valute>
            <Valute ID="17"><NumCode>826</NumCode><CharCode>GBP</CharCode><Nominal>1</Nominal><Name>Pound Sterling</Name><Value>23.1000</Value></Valute>
        </ValCurs>
        """;

    private static HttpServer mockBnm;
    private static final AtomicBoolean failBnm = new AtomicBoolean(false);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rates_db")
            .withUsername("rate_app")
            .withPassword("rate_test_pw");

    @BeforeAll
    static void startMockBnm() throws Exception {
        mockBnm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockBnm.createContext("/bnm", exchange -> {
            if (failBnm.get()) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            String body = (query != null && query.contains("date=25.09.2026"))
                    ? SAMPLE_XML
                    : "<?xml version=\"1.0\" encoding=\"utf-8\"?><ValCurs Date=\"27.09.2026\" name=\"Empty\"></ValCurs>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        mockBnm.start();
    }

    @AfterAll
    static void stopMockBnm() {
        if (mockBnm != null) {
            mockBnm.stop(0);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("rate.bnm.base-url", () -> "http://127.0.0.1:" + mockBnm.getAddress().getPort() + "/bnm");
        registry.add("rate.bnm.max-rollback-days", () -> 7);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExchangeRateRepository repository;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Test
    void weekendRollbackCachingOfflineFallbackAndHealth() throws Exception {
        failBnm.set(false);
        exchangeRateService.clearNegativeCache();

        // 1. Sunday 2026-09-27 rolls back 2 days to Friday 2026-09-25 from mock BNM and persists to PostgreSQL
        mockMvc.perform(get("/api/v1/currencies").param("date", "2026-09-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateDate").value("2026-09-25"))
                .andExpect(jsonPath("$.requestedDate").value("2026-09-27"))
                .andExpect(jsonPath("$.rollbackDaysApplied").value(2))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.offline").value(false))
                .andExpect(jsonPath("$.currencies.length()").value(6)); // 5 valutes + base MDL

        // 2. Subsequent query for 2026-09-25 hits PostgreSQL cache directly
        mockMvc.perform(get("/api/v1/rates").param("date", "2026-09-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.offline").value(false));

        // 3. When BNM is unreachable for an uncached date, fallback serves latest persisted DB date (offline=true)
        failBnm.set(true);
        mockMvc.perform(get("/api/v1/currencies").param("date", "2026-10-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.offline").value(true))
                .andExpect(jsonPath("$.rateDate").value("2026-09-25"));

        // 4. Future date -> 400 RFC 9457
        mockMvc.perform(get("/api/v1/currencies").param("date", LocalDate.now().plusDays(10).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 5. Empty DB + BNM failure -> 503 RFC 9457
        repository.deleteAll();
        mockMvc.perform(get("/api/v1/currencies").param("date", "2026-10-06"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // 6. Health & readiness
        mockMvc.perform(get("/health")).andExpect(status().isOk());
        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.database").value("ok"));
    }
}
