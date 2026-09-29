package com.converter.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionRequestDto(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be strictly greater than zero")
    BigDecimal amount,

    @NotBlank(message = "Source currency code is required")
    @Size(min = 3, max = 3, message = "Source currency code must be exactly 3 characters")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Source currency code must consist of 3 letters")
    String sourceCurrency,

    @NotBlank(message = "Target currency code is required")
    @Size(min = 3, max = 3, message = "Target currency code must be exactly 3 characters")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Target currency code must consist of 3 letters")
    String targetCurrency,

    LocalDate date
) {}
