package com.converter.common.health;

import java.time.Instant;
import java.util.Map;

public record HealthResponseDto(
        String status,
        String service,
        String hostname,
        Instant timestamp
) {
    public record ReadinessResponseDto(
            String status,
            String service,
            String hostname,
            Map<String, String> checks,
            Instant timestamp
    ) {}
}
