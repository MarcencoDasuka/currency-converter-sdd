package com.converter.service;

import com.converter.config.AppProperties;
import com.converter.entity.ExchangeRateEntity;
import com.converter.exception.BulletinUnavailableException;
import com.converter.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceRollbackTest {

    @Mock
    private BnmClient bnmClient;

    @Mock
    private BnmXmlParser bnmXmlParser;

    @Mock
    private ExchangeRateRepository repository;

    private ExchangeRateService service;

    private final AppProperties properties = new AppProperties(
            new AppProperties.Bnm("https://www.bnm.md", 5, 10, 7),
            new AppProperties.Cors(List.of("http://localhost:5173"))
    );

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(bnmClient, bnmXmlParser, repository, properties);
    }

    @Test
    @DisplayName("Should successfully apply bounded rollback when weekend date returns empty bulletin")
    void getRates_WeekendDates_RollsBackToValidPrecedingDate() {
        LocalDate sunday = LocalDate.of(2026, 9, 27);
        LocalDate saturday = LocalDate.of(2026, 9, 26);
        LocalDate friday = LocalDate.of(2026, 9, 25);

        // Sunday and Saturday are not in local cache
        when(repository.findByRateDate(sunday)).thenReturn(List.of());
        when(repository.findByRateDate(saturday)).thenReturn(List.of());

        // Sunday and Saturday return empty/unparseable XML from BNM
        when(bnmClient.fetchBulletinXml(sunday)).thenReturn("<empty/>");
        when(bnmClient.fetchBulletinXml(saturday)).thenReturn("<empty/>");
        when(bnmXmlParser.parse("<empty/>", sunday)).thenReturn(new BnmXmlParser.ParsedBulletin(sunday, "Empty", List.of()));
        when(bnmXmlParser.parse("<empty/>", saturday)).thenReturn(new BnmXmlParser.ParsedBulletin(saturday, "Empty", List.of()));

        // Friday is cached in DB
        List<ExchangeRateEntity> fridayRates = createSampleRates(friday);
        when(repository.findByRateDate(friday)).thenReturn(fridayRates);

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

        // All dates return empty
        when(repository.findByRateDate(any(LocalDate.class))).thenReturn(List.of());
        when(bnmClient.fetchBulletinXml(any(LocalDate.class))).thenThrow(new IllegalStateException("Network unreachable"));

        // Latest available date in DB is 2 weeks ago
        LocalDate historicalDate = LocalDate.of(2026, 9, 10);
        List<ExchangeRateEntity> historicalRates = createSampleRates(historicalDate);
        when(repository.findLatestAvailableRateDate()).thenReturn(Optional.of(historicalDate));
        when(repository.findByRateDate(eq(historicalDate))).thenReturn(historicalRates);

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

        when(repository.findByRateDate(any(LocalDate.class))).thenReturn(List.of());
        when(bnmClient.fetchBulletinXml(any(LocalDate.class))).thenThrow(new IllegalStateException("Network down"));
        when(repository.findLatestAvailableRateDate()).thenReturn(Optional.empty());

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
