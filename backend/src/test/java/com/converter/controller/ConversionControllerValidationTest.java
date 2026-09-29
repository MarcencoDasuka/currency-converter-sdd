package com.converter.controller;

import com.converter.dto.ConversionRequestDto;
import com.converter.dto.ConversionResponseDto;
import com.converter.exception.GlobalExceptionHandler;
import com.converter.service.CurrencyConversionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversionControllerValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CurrencyConversionService stubService = new CurrencyConversionService(null) {
            @Override
            public ConversionResponseDto convert(ConversionRequestDto req) {
                return new ConversionResponseDto(
                        new BigDecimal("100.00"), "USD", "EUR",
                        new BigDecimal("91.6452"), new BigDecimal("0.916452"),
                        LocalDate.of(2026, 9, 28), "National Bank of Moldova", false, false, 0
                );
            }
        };

        ConversionController controller = new ConversionController(stubService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should return 400 with RFC 9457 Problem Details when amount is negative")
    void convert_NegativeAmount_ReturnsRfc9457ProblemDetail() throws Exception {
        String jsonPayload = """
            {
                "amount": -50.00,
                "sourceCurrency": "USD",
                "targetCurrency": "EUR"
            }
            """;

        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.invalidParams[0].name").value("amount"))
                .andExpect(jsonPath("$.invalidParams[0].reason").value("Amount must be strictly greater than zero"));
    }

    @Test
    @DisplayName("Should return 400 when amount is zero")
    void convert_ZeroAmount_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
            {
                "amount": 0.00,
                "sourceCurrency": "USD",
                "targetCurrency": "EUR"
            }
            """;

        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidParams[0].name").value("amount"));
    }

    @Test
    @DisplayName("Should return 400 when currency code has invalid format")
    void convert_InvalidCurrencyCode_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
            {
                "amount": 100.00,
                "sourceCurrency": "US1",
                "targetCurrency": "EURO"
            }
            """;

        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 200 OK when request is valid")
    void convert_ValidRequest_ReturnsOk() throws Exception {
        String jsonPayload = """
            {
                "amount": 100.00,
                "sourceCurrency": "USD",
                "targetCurrency": "EUR"
            }
            """;

        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(91.6452))
                .andExpect(jsonPath("$.sourceCurrency").value("USD"))
                .andExpect(jsonPath("$.targetCurrency").value("EUR"));
    }
}
