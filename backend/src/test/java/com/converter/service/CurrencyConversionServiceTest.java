package com.converter.service;

import com.converter.config.AppProperties;
import com.converter.dto.ConversionRequestDto;
import com.converter.dto.ConversionResponseDto;
import com.converter.entity.ExchangeRateEntity;
import com.converter.exception.CurrencyNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyConversionServiceTest {

    private CurrencyConversionService conversionService;
    private final LocalDate testDate = LocalDate.of(2026, 9, 28);

    @BeforeEach
    void setUp() {
        ExchangeRateEntity mdl = new ExchangeRateEntity(
                498, "MDL", "Moldovan Leu", 1, BigDecimal.ONE, testDate, Instant.now(), "MDL"
        );
        ExchangeRateEntity usd = new ExchangeRateEntity(
                840, "USD", "US Dollar", 1, new BigDecimal("17.8250"), testDate, Instant.now(), "47"
        );
        ExchangeRateEntity eur = new ExchangeRateEntity(
                978, "EUR", "Euro", 1, new BigDecimal("19.4500"), testDate, Instant.now(), "44"
        );
        ExchangeRateEntity jpy = new ExchangeRateEntity(
                392, "JPY", "Japanese Yen", 100, new BigDecimal("11.8500"), testDate, Instant.now(), "39"
        );

        ExchangeRateService.ResolvedBulletin bulletin = new ExchangeRateService.ResolvedBulletin(
                testDate, testDate, "National Bank of Moldova", false, false, 0,
                List.of(mdl, usd, eur, jpy)
        );

        AppProperties dummyProperties = new AppProperties(
                new AppProperties.Bnm("https://www.bnm.md", 5, 10, 7),
                new AppProperties.Cors(List.of("http://localhost:5173"))
        );

        ExchangeRateService stubService = new ExchangeRateService(null, null, null, dummyProperties) {
            @Override
            public ResolvedBulletin getRatesForDate(LocalDate date) {
                return bulletin;
            }
        };

        conversionService = new CurrencyConversionService(stubService);
    }

    @Test
    @DisplayName("Should convert base currency MDL to foreign currency EUR accurately")
    void convert_MdlToEur_DirectConversion() {
        ConversionRequestDto request = new ConversionRequestDto(
                new BigDecimal("100.00"), "MDL", "EUR", testDate
        );

        ConversionResponseDto response = conversionService.convert(request);

        assertThat(response.convertedAmount()).isEqualByComparingTo(new BigDecimal("5.1414"));
        assertThat(response.effectiveRate()).isEqualByComparingTo(new BigDecimal("0.051414"));
        assertThat(response.sourceCurrency()).isEqualTo("MDL");
        assertThat(response.targetCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Should convert foreign currency EUR to base currency MDL accurately (reverse direction)")
    void convert_EurToMdl_ReverseConversion() {
        ConversionRequestDto request = new ConversionRequestDto(
                new BigDecimal("100.00"), "EUR", "MDL", testDate
        );

        ConversionResponseDto response = conversionService.convert(request);

        assertThat(response.convertedAmount()).isEqualByComparingTo(new BigDecimal("1945.0000"));
        assertThat(response.effectiveRate()).isEqualByComparingTo(new BigDecimal("19.450000"));
    }

    @Test
    @DisplayName("Should convert cross currencies USD to EUR through base MDL accurately")
    void convert_UsdToEur_CrossConversion() {
        ConversionRequestDto request = new ConversionRequestDto(
                new BigDecimal("100.00"), "USD", "EUR", testDate
        );

        ConversionResponseDto response = conversionService.convert(request);

        assertThat(response.convertedAmount()).isEqualByComparingTo(new BigDecimal("91.6452"));
        assertThat(response.effectiveRate()).isEqualByComparingTo(new BigDecimal("0.916452"));
    }

    @Test
    @DisplayName("Should properly account for nominal > 1 (e.g. 100 JPY)")
    void convert_UsdToJpy_AccountsForNominal() {
        ConversionRequestDto request = new ConversionRequestDto(
                new BigDecimal("100.00"), "USD", "JPY", testDate
        );

        ConversionResponseDto response = conversionService.convert(request);

        // 100 USD in JPY = 100 * (17.8250 / 0.1185) = 15042.1941
        assertThat(response.convertedAmount()).isEqualByComparingTo(new BigDecimal("15042.1941"));
        assertThat(response.effectiveRate()).isEqualByComparingTo(new BigDecimal("150.421941"));
    }

    @Test
    @DisplayName("Should return identity result for identical source and target currencies without error")
    void convert_SameCurrency_ReturnsIdentity() {
        ConversionRequestDto request = new ConversionRequestDto(
                new BigDecimal("250.75"), "EUR", "EUR", testDate
        );

        ConversionResponseDto response = conversionService.convert(request);

        assertThat(response.convertedAmount()).isEqualByComparingTo(new BigDecimal("250.7500"));
        assertThat(response.effectiveRate()).isEqualByComparingTo(new BigDecimal("1.000000"));
    }

    @Test
    @DisplayName("Should throw CurrencyNotFoundException when currency does not exist in bulletin")
    void convert_UnknownCurrency_ThrowsException() {
        ConversionRequestDto request = new ConversionRequestDto(
                new BigDecimal("100.00"), "XYZ", "EUR", testDate
        );

        assertThatThrownBy(() -> conversionService.convert(request))
                .isInstanceOf(CurrencyNotFoundException.class)
                .hasMessageContaining("XYZ");
    }

    @Test
    @DisplayName("Should reject non-positive or null amount")
    void convert_InvalidAmount_ThrowsIllegalArgumentException() {
        ConversionRequestDto zero = new ConversionRequestDto(BigDecimal.ZERO, "USD", "EUR", testDate);
        ConversionRequestDto negative = new ConversionRequestDto(new BigDecimal("-50"), "USD", "EUR", testDate);
        ConversionRequestDto nullAmount = new ConversionRequestDto(null, "USD", "EUR", testDate);

        assertThatThrownBy(() -> conversionService.convert(zero)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conversionService.convert(negative)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conversionService.convert(nullAmount)).isInstanceOf(IllegalArgumentException.class);
    }
}
