package com.converter.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionResponseDto(
        BigDecimal amount,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal convertedAmount,
        BigDecimal effectiveRate,
        LocalDate rateDate,
        String source,
        boolean cached,
        boolean offline,
        int rollbackDaysApplied
) {}
