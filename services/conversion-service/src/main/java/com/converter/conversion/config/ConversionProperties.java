package com.converter.conversion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conversion")
public record ConversionProperties(
        String rateServiceUrl,
        int rateConnectTimeoutSeconds,
        int rateReadTimeoutSeconds,
        long outboxPollMs
) {
    public ConversionProperties {
        if (rateServiceUrl == null || rateServiceUrl.isBlank()) {
            rateServiceUrl = "http://rate-service:8082";
        }
        if (rateConnectTimeoutSeconds <= 0) {
            rateConnectTimeoutSeconds = 2;
        }
        if (rateReadTimeoutSeconds <= 0) {
            rateReadTimeoutSeconds = 8;
        }
        if (outboxPollMs <= 0) {
            outboxPollMs = 1500L;
        }
    }
}
