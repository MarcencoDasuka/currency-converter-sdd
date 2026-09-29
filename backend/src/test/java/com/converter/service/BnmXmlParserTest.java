package com.converter.service;

import com.converter.entity.ExchangeRateEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BnmXmlParserTest {

    private BnmXmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new BnmXmlParser();
    }

    @Test
    @DisplayName("Should parse valid BNM XML with nominal and various currencies successfully")
    void parse_ValidXml_ReturnsParsedBulletin() {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ValCurs Date="28.09.2026" name="Official exchange rates">
                <Valute ID="47">
                    <NumCode>840</NumCode>
                    <CharCode>USD</CharCode>
                    <Nominal>1</Nominal>
                    <Name>US Dollar</Name>
                    <Value>17.8250</Value>
                </Valute>
                <Valute ID="44">
                    <NumCode>978</NumCode>
                    <CharCode>EUR</CharCode>
                    <Nominal>1</Nominal>
                    <Name>Euro</Name>
                    <Value>19.4500</Value>
                </Valute>
                <Valute ID="39">
                    <NumCode>392</NumCode>
                    <CharCode>JPY</CharCode>
                    <Nominal>100</Nominal>
                    <Name>Japanese Yen</Name>
                    <Value>11.8500</Value>
                </Valute>
            </ValCurs>
            """;

        BnmXmlParser.ParsedBulletin result = parser.parse(xml, LocalDate.of(2026, 9, 28));

        assertThat(result).isNotNull();
        assertThat(result.bulletinDate()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(result.rates()).hasSize(3);

        ExchangeRateEntity usd = result.rates().stream()
                .filter(r -> "USD".equals(r.getCurrencyCode()))
                .findFirst()
                .orElseThrow();
        assertThat(usd.getNominal()).isEqualTo(1);
        assertThat(usd.getRate()).isEqualByComparingTo(new BigDecimal("17.8250"));
        assertThat(usd.getNumericCode()).isEqualTo(840);

        ExchangeRateEntity jpy = result.rates().stream()
                .filter(r -> "JPY".equals(r.getCurrencyCode()))
                .findFirst()
                .orElseThrow();
        assertThat(jpy.getNominal()).isEqualTo(100);
        assertThat(jpy.getRate()).isEqualByComparingTo(new BigDecimal("11.8500"));
    }

    @Test
    @DisplayName("Should handle comma as decimal separator in XML values")
    void parse_CommaDecimalSeparator_ParsesCorrectly() {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ValCurs Date="28.09.2026" name="Official exchange rates">
                <Valute ID="47">
                    <NumCode>840</NumCode>
                    <CharCode>USD</CharCode>
                    <Nominal>1</Nominal>
                    <Name>US Dollar</Name>
                    <Value>17,8250</Value>
                </Valute>
            </ValCurs>
            """;

        BnmXmlParser.ParsedBulletin result = parser.parse(xml, LocalDate.of(2026, 9, 28));

        assertThat(result.rates()).hasSize(1);
        assertThat(result.rates().get(0).getRate()).isEqualByComparingTo(new BigDecimal("17.8250"));
    }

    @Test
    @DisplayName("Should reject XXE injection attempt due to disallow-doctype-decl")
    void parse_XxePayload_ThrowsIllegalArgumentException() {
        String xxePayload = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE ValCurs [
                <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <ValCurs Date="28.09.2026" name="Official exchange rates">
                <Valute ID="47">
                    <NumCode>840</NumCode>
                    <CharCode>&xxe;</CharCode>
                    <Nominal>1</Nominal>
                    <Name>US Dollar</Name>
                    <Value>17.8250</Value>
                </Valute>
            </ValCurs>
            """;

        assertThatThrownBy(() -> parser.parse(xxePayload, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOCTYPE is disallowed");
    }

    @Test
    @DisplayName("Should throw exception for malformed XML")
    void parse_MalformedXml_ThrowsIllegalArgumentException() {
        String malformed = "<ValCurs><Valute><CharCode>USD</ValCurs>";

        assertThatThrownBy(() -> parser.parse(malformed, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw exception for empty or null content")
    void parse_EmptyContent_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> parser.parse("", LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(null, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
