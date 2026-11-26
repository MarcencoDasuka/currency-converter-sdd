package com.converter.conversion.dto;

import com.converter.common.dto.ConversionResponseDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ConversionDtos {

    private ConversionDtos() {}

    public record ConversionHistoryItem(
            UUID id,
            UUID userId,
            BigDecimal amount,
            String sourceCurrency,
            String targetCurrency,
            BigDecimal convertedAmount,
            BigDecimal effectiveRate,
            LocalDate rateDate,
            String source,
            boolean offline,
            Instant createdAt
    ) {}

    public record ConversionExecutionResult(
            ConversionResponseDto response,
            UUID savedConversionId
    ) {}
}
