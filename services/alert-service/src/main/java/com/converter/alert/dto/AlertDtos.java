package com.converter.alert.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AlertDtos {

    private AlertDtos() {}

    public record CreateAlertRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Source currency must be 3 uppercase ISO letters")
            String sourceCurrency,

            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Target currency must be 3 uppercase ISO letters")
            String targetCurrency,

            @NotBlank @Pattern(regexp = "^(ABOVE|BELOW)$", message = "Direction must be ABOVE or BELOW")
            String direction,

            @NotNull @DecimalMin(value = "0.000001", message = "Target rate must be positive")
            @DecimalMax(value = "1000000", message = "Target rate is too large")
            BigDecimal targetRate
    ) {}

    public record AlertResponse(
            UUID id,
            UUID userId,
            String sourceCurrency,
            String targetCurrency,
            String direction,
            BigDecimal targetRate,
            boolean active,
            BigDecimal triggeredRate,
            Instant triggeredAt,
            Instant createdAt
    ) {}

    public record EvaluateAlertsResponse(
            int evaluatedCount,
            int triggeredCount
    ) {}
}
