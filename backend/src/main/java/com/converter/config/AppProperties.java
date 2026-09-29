package com.converter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    Bnm bnm,
    Cors cors
) {
    public record Bnm(
        String baseUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int maxRollbackDays
    ) {}

    public record Cors(
        List<String> allowedOrigins
    ) {}
}
