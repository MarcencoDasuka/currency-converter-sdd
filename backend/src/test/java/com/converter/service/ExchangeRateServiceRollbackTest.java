package com.converter.service;

import com.converter.config.AppProperties;
import com.converter.entity.ExchangeRateEntity;
import com.converter.exception.BulletinUnavailableException;
import com.converter.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRateServiceRollbackTest {

    private final AppProperties properties = new AppProperties(
            new AppProperties.Bnm("https://www.bnm.md", 5, 10, 7),
            new AppProperties.Cors(List.of("http://localhost:5173"))
    );

    private final Map<LocalDate, List<ExchangeRateEntity>> dbCache = new HashMap<>();
    private Optional<LocalDate> latestDateInDb = Optional.empty();

    private final Map<LocalDate, String> bnmXmlResponses = new HashMap<>();
    private boolean bnmNetworkFails = false;

    private ExchangeRateRepository repository;
    private BnmClient bnmClient;
    private BnmXmlParser bnmXmlParser;
    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        dbCache.clear();
        latestDateInDb = Optional.empty();
        bnmXmlResponses.clear();
        bnmNetworkFails = false;

        repository = (ExchangeRateRepository) Proxy.newProxyInstance(
                ExchangeRateRepository.class.getClassLoader(),
                new Class<?>[]{ExchangeRateRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByRateDate".equals(name)) {
                        LocalDate d = (LocalDate) args[0];
                        return dbCache.getOrDefault(d, List.of());
                    }
                    if ("findLatestAvailableRateDate".equals(name)) {
                        return latestDateInDb;
                    }
                    if ("upsertRate".equals(name)) {
                        return null;
                    }
                    return null;
                }
        );

        bnmClient = new BnmClient(null, properties) {
            @Override
            public String fetchBulletinXml(LocalDate date) {
                if (bnmNetworkFails) {
                    throw new IllegalStateException("Network unreachable");
                }
                return bnmXmlResponses.getOrDefault(date, "<empty/>");
            }
        };

        bnmXmlParser = new BnmXmlParser() {
            @Override
            public ParsedBulletin parse(String xmlContent, LocalDate fallbackDate) {
                if ("<empty/>".equals(xmlContent)) {
                    return new ParsedBulletin(fallbackDate, "Empty", List.of());
                }
                return super.parse(xmlContent, fallbackDate);
            }
        };

        service = new ExchangeRateService(bnmClient, bnmXmlParser, repository, properties);
    }

    @Test
    @DisplayName("Should successfully apply bounded rollback when weekend date returns empty bulletin")
    void getRates_WeekendDates_RollsBackToValidPrecedingDate() {
        LocalDate sunday = LocalDate.of(2026, 9, 27);
        LocalDate saturday = LocalDate.of(2026, 9, 26);
        LocalDate friday = LocalDate.of(2026, 9, 25);

        // Friday is cached in DB
        List<ExchangeRateEntity> fridayRates = createSampleRates(friday);
        dbCache.put(friday, fridayRates);

        // Sunday and Saturday return empty bulletin from BNM
        bnmXmlResponses.put(sunday, "<empty/>");
        bnmXmlResponses.put(saturday, "<empty/>");

        ExchangeRateService.ResolvedBulletin result = service.getRatesForDate(sunday);

        assertThat(result).isNotNull();
        assertThat(result.requestedDate()).isEqualTo(sunday);
        assertThat(result.rateDate()).isEqualTo(friday);
        assertThat(result.rollbackDaysApplied()).isEqualTo(2);
        assertThat(result.cached()).isTrue();
    }

    @Test
    @DisplayName("Should fallback to latest DB bulletin when rollback limit is reached")
    void getRates_RollbackExceeded_FallsBackToLatestDbCache() {
        LocalDate requested = LocalDate.of(2026, 9, 28);
        bnmNetworkFails = true;

        // Latest available date in DB is 2 weeks ago
        LocalDate historicalDate = LocalDate.of(2026, 9, 10);
        List<ExchangeRateEntity> historicalRates = createSampleRates(historicalDate);
        latestDateInDb = Optional.of(historicalDate);
        dbCache.put(historicalDate, historicalRates);

        ExchangeRateService.ResolvedBulletin result = service.getRatesForDate(requested);

        assertThat(result).isNotNull();
        assertThat(result.rateDate()).isEqualTo(historicalDate);
        assertThat(result.offline()).isTrue();
        assertThat(result.cached()).isTrue();
    }

    @Test
    @DisplayName("Should throw BulletinUnavailableException when rollback exhausted and DB is completely empty")
    void getRates_RollbackExhaustedAndDbEmpty_ThrowsException() {
        LocalDate requested = LocalDate.of(2026, 9, 28);
        bnmNetworkFails = true;
        latestDateInDb = Optional.empty();

        assertThatThrownBy(() -> service.getRatesForDate(requested))
                .isInstanceOf(BulletinUnavailableException.class)
                .hasMessageContaining("No valid rate bulletin available");
    }

    private List<ExchangeRateEntity> createSampleRates(LocalDate date) {
        List<ExchangeRateEntity> list = new ArrayList<>();
        list.add(new ExchangeRateEntity(840, "USD", "US Dollar", 1, new BigDecimal("17.8000"), date, Instant.now(), "47"));
        list.add(new ExchangeRateEntity(978, "EUR", "Euro", 1, new BigDecimal("19.5000"), date, Instant.now(), "44"));
        list.add(new ExchangeRateEntity(946, "RON", "Romanian Leu", 1, new BigDecimal("3.9200"), date, Instant.now(), "12"));
        list.add(new ExchangeRateEntity(980, "UAH", "Hryvnia", 1, new BigDecimal("0.4300"), date, Instant.now(), "23"));
        list.add(new ExchangeRateEntity(826, "GBP", "Pound", 1, new BigDecimal("23.1000"), date, Instant.now(), "55"));
        return list;
    }
}
