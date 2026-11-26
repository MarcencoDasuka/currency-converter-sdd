package com.converter.common.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionRequestDto(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.0001", message = "Amount must be strictly greater than 0")
        @DecimalMax(value = "1000000000000", message = "Amount exceeds maximum allowed limit")
        BigDecimal amount,

        @NotBlank(message = "Source currency code is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Source currency must be a valid 3-letter ISO 4217 uppercase code")
        String sourceCurrency,

        @NotBlank(message = "Target currency code is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Target currency must be a valid 3-letter ISO 4217 uppercase code")
        String targetCurrency,

        LocalDate date
) {}
