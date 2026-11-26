package com.converter.rate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate.bnm")
public record RateProperties(
        String baseUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int maxRollbackDays
) {
    public RateProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://www.bnm.md/en/official_exchange_rates";
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 3;
        }
        if (readTimeoutSeconds <= 0) {
            readTimeoutSeconds = 8;
        }
        if (maxRollbackDays < 0 || maxRollbackDays > 30) {
            maxRollbackDays = 7;
        }
    }
}
