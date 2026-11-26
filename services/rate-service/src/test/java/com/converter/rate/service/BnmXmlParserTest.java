package com.converter.rate.service;

import com.converter.rate.entity.ExchangeRateEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BnmXmlParserTest {

    private BnmXmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new BnmXmlParser();
    }

    @Test
    void parse_ValidXmlWithNominalAndComma_ParsesAccurately() {
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
        assertThat(result.bulletinDate()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(result.rates()).hasSize(2);
        ExchangeRateEntity jpy = result.rates().stream().filter(r -> "JPY".equals(r.getCurrencyCode())).findFirst().orElseThrow();
        assertThat(jpy.getNominal()).isEqualTo(100);
        assertThat(jpy.getRate()).isEqualByComparingTo(new BigDecimal("11.8500"));
    }

    @Test
    void parse_XxePayload_IsRejected() {
        String xxePayload = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE ValCurs [
                <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <ValCurs Date="28.09.2026" name="Official exchange rates">
                <Valute ID="47"><NumCode>840</NumCode><CharCode>&xxe;</CharCode><Nominal>1</Nominal><Name>USD</Name><Value>17.8</Value></Valute>
            </ValCurs>
            """;

        assertThatThrownBy(() -> parser.parse(xxePayload, LocalDate.of(2026, 9, 28)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOCTYPE");
    }
}
