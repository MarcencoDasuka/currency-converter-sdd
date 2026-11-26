package com.converter.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        Upstreams upstreams,
        RateLimit rateLimit,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        long maxBodyBytes,
        boolean trustProxyHeaders,
        List<String> corsAllowedOrigins
) {
    public GatewayProperties {
        if (upstreams == null) {
            upstreams = new Upstreams(
                    "http://auth-service:8081",
                    "http://rate-service:8082",
                    "http://conversion-service:8083",
                    "http://alert-service:8084",
                    "http://notification-service:8085"
            );
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(true, 120, 30, 60);
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 3;
        }
        if (readTimeoutSeconds <= 0) {
            readTimeoutSeconds = 15;
        }
        if (maxBodyBytes <= 0) {
            maxBodyBytes = 1024L * 1024L;
        }
        if (corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()) {
            corsAllowedOrigins = List.of(
                    "http://localhost:3001",
                    "http://127.0.0.1:3001",
                    "http://localhost:5173",
                    "http://127.0.0.1:5173"
            );
        }
    }

    public record Upstreams(
            String authServiceUrl,
            String rateServiceUrl,
            String conversionServiceUrl,
            String alertServiceUrl,
            String notificationServiceUrl
    ) {}

    public record RateLimit(
            boolean enabled,
            int defaultRequestsPerWindow,
            int authRequestsPerWindow,
            int windowSeconds
    ) {
        public RateLimit {
            if (defaultRequestsPerWindow <= 0) defaultRequestsPerWindow = 120;
            if (authRequestsPerWindow <= 0) authRequestsPerWindow = 30;
            if (windowSeconds <= 0) windowSeconds = 60;
        }
    }
}
