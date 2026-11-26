package com.converter.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.jwt")
public record JwtProperties(
        String secret,
        long accessTokenTtlSeconds,
        String issuer
) {
    public JwtProperties {
        if (accessTokenTtlSeconds <= 0) {
            accessTokenTtlSeconds = 3600L;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "currency-platform-auth";
        }
    }
}
